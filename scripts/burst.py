#!/usr/bin/env python3
"""
High-Throughput Concurrency Burst Test & Reconciliation Engine
Simulates the on-sale stampede against the Paytm Money Seat Reservation System.
"""

import argparse
import asyncio
import sys
import time
import uuid
from typing import Dict, List, Any
import aiohttp

# ANSI Color codes for clean CLI reporting
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"


async def fetch_token(session: aiohttp.ClientSession, base_url: str, user_id: str, role: str = "USER") -> str:
    url = f"{base_url}/auth/token?userId={user_id}&role={role}"
    async with session.post(url) as resp:
        if resp.status != 200:
            text = await resp.text()
            raise RuntimeError(f"Failed to generate token for {user_id}: HTTP {resp.status} - {text}")
        data = await resp.json()
        return data["token"]


async def create_show(session: aiohttp.ClientSession, base_url: str, admin_token: str) -> Dict[str, Any]:
    # 100 seats: A1-A25, B1-B25, C1-C25, D1-D25
    seat_labels = []
    for row in ["A", "B", "C", "D"]:
        for num in range(1, 26):
            seat_labels.append(f"{row}{num}")

    payload = {
        "name": f"Mega On-Sale Arena - {uuid.uuid4().hex[:6]}",
        "pricePaise": 25000,
        "perUserLimit": 4,
        "totalSeats": len(seat_labels),
        "seatNumbers": seat_labels
    }

    headers = {
        "Authorization": f"Bearer {admin_token}",
        "Content-Type": "application/json"
    }

    async with session.post(f"{base_url}/shows", json=payload, headers=headers) as resp:
        if resp.status != 201:
            text = await resp.text()
            raise RuntimeError(f"Failed to create show: HTTP {resp.status} - {text}")
        return await resp.json()


async def send_reservation(session: aiohttp.ClientSession, base_url: str, token: str, show_id: str,
                           seats: List[str], idempotency_key: str) -> int:
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "Idempotency-Key": idempotency_key
    }
    payload = {
        "showId": show_id,
        "seatNumbers": seats,
        "idempotencyKey": idempotency_key
    }
    start = time.perf_counter()
    try:
        async with session.post(f"{base_url}/shows/{show_id}/reserve", json=payload, headers=headers) as resp:
            status = resp.status
            # read body to ensure connection reuse
            await resp.read()
            return status
    except Exception as e:
        return 599  # Client-side connection error


async def run_burst_test(base_url: str):
    print(f"\n{BOLD}{CYAN}======================================================================{RESET}")
    print(f"{BOLD}{CYAN}   Paytm Money Seat Reservation at Scale — High Concurrency Burst    {RESET}")
    print(f"{BOLD}{CYAN}======================================================================{RESET}\n")
    print(f"Target Base URL: {BOLD}{base_url}{RESET}")

    connector = aiohttp.TCPConnector(limit=700, ttl_dns_cache=300)
    timeout = aiohttp.ClientTimeout(total=45)

    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        # Pre-flight health check
        try:
            async with session.get(f"{base_url}/actuator/health") as health_resp:
                if health_resp.status != 200:
                    print(f"{YELLOW}Warning: /actuator/health returned HTTP {health_resp.status}{RESET}")
                else:
                    print(f"{GREEN}✓ Health probe UP (HTTP 200){RESET}")
        except Exception as e:
            print(f"{RED}Error: Cannot connect to {base_url}: {e}{RESET}")
            sys.exit(1)

        print("\n[Setup] Minting Admin Token and Creating 100-Seat Show...")
        admin_token = await fetch_token(session, base_url, "admin_user", role="ADMIN")
        show = await create_show(session, base_url, admin_token)
        show_id = show["id"]
        total_seats = show["totalSeats"]
        print(f"{GREEN}✓ Created Show ID: {show_id} with {total_seats} seats{RESET}")

        summary = {}

        # -------------------------------------------------------------
        # Phase 1: Hot Seat Storm (500 Concurrent Threads on Seat A12)
        # -------------------------------------------------------------
        print(f"\n{BOLD}[Phase 1] Hot Seat Storm: 500 concurrent requests competing for single seat 'A12'...{RESET}")
        hot_tokens = await asyncio.gather(*[
            fetch_token(session, base_url, f"hot_user_{i}") for i in range(500)
        ])
        p1_tasks = [
            send_reservation(session, base_url, hot_tokens[i], show_id, ["A12"], str(uuid.uuid4()))
            for i in range(500)
        ]
        p1_start = time.perf_counter()
        p1_results = await asyncio.gather(*p1_tasks)
        p1_duration = time.perf_counter() - p1_start

        c201 = p1_results.count(201)
        c409 = p1_results.count(409)
        c5xx = sum(1 for s in p1_results if s >= 500)
        p1_pass = (c201 == 1) and (c409 == 499) and (c5xx == 0)

        summary["Phase 1: Hot Seat (500 reqs)"] = {
            "201": c201, "409": c409, "5xx": c5xx,
            "Duration": f"{p1_duration:.2f}s",
            "Expected": "1x 201, 499x 409, 0x 5xx",
            "Pass": p1_pass
        }

        # -------------------------------------------------------------
        # Phase 2: Per-User Limit Storm (1 User, 10 Parallel Requests)
        # -------------------------------------------------------------
        print(f"\n{BOLD}[Phase 2] Per-User Limit Storm: 1 user sends 10 parallel requests (limit = 4)...{RESET}")
        greedy_user_token = await fetch_token(session, base_url, "greedy_user_burst")
        seats_p2 = [f"B{i}" for i in range(1, 11)]
        p2_tasks = [
            send_reservation(session, base_url, greedy_user_token, show_id, [seats_p2[i]], str(uuid.uuid4()))
            for i in range(10)
        ]
        p2_start = time.perf_counter()
        p2_results = await asyncio.gather(*p2_tasks)
        p2_duration = time.perf_counter() - p2_start

        p2_201 = p2_results.count(201)
        p2_409 = p2_results.count(409)
        p2_5xx = sum(1 for s in p2_results if s >= 500)
        p2_pass = (p2_201 == 4) and (p2_409 == 6) and (p2_5xx == 0)

        summary["Phase 2: User Limit (10 reqs)"] = {
            "201": p2_201, "409": p2_409, "5xx": p2_5xx,
            "Duration": f"{p2_duration:.2f}s",
            "Expected": "4x 201, 6x 409, 0x 5xx",
            "Pass": p2_pass
        }

        # -------------------------------------------------------------
        # Phase 3: Idempotent Retries (50 Concurrent Requests, Same Key)
        # -------------------------------------------------------------
        print(f"\n{BOLD}[Phase 3] Idempotent Retries: 50 concurrent requests with IDENTICAL key on 'C1'...{RESET}")
        idempotent_user_token = await fetch_token(session, base_url, "idempotent_user_burst")
        shared_key = f"idempotent-replay-{uuid.uuid4().hex}"
        p3_tasks = [
            send_reservation(session, base_url, idempotent_user_token, show_id, ["C1"], shared_key)
            for _ in range(50)
        ]
        p3_start = time.perf_counter()
        p3_results = await asyncio.gather(*p3_tasks)
        p3_duration = time.perf_counter() - p3_start

        p3_201 = p3_results.count(201)
        p3_200 = p3_results.count(200)
        p3_5xx = sum(1 for s in p3_results if s >= 500)
        p3_valid = (p3_201 + p3_200 == 50) and (p3_201 >= 1) and (p3_5xx == 0)

        summary["Phase 3: Idempotent Retries (50 reqs)"] = {
            "201": p3_201, "200": p3_200, "5xx": p3_5xx,
            "Duration": f"{p3_duration:.2f}s",
            "Expected": "50 valid (201/200), 0x 5xx",
            "Pass": p3_valid
        }

        # -------------------------------------------------------------
        # Phase 4: Idempotency Key Collision (Same Key, Different Seat)
        # -------------------------------------------------------------
        print(f"\n{BOLD}[Phase 4] Key Collision Check: Reusing key with altered payload (Seat 'C2')...{RESET}")
        p4_status = await send_reservation(session, base_url, idempotent_user_token, show_id, ["C2"], shared_key)
        p4_pass = (p4_status == 409)

        summary["Phase 4: Key Collision (1 req)"] = {
            "Status": p4_status,
            "Expected": "409 Conflict",
            "Pass": p4_pass
        }

        # -------------------------------------------------------------
        # Phase 5: Reconciliation Verification
        # -------------------------------------------------------------
        print(f"\n{BOLD}[Phase 5] Reconciliation Query: Verifying seat integrity invariant...{RESET}")
        async with session.get(f"{base_url}/shows/{show_id}/seats") as seat_resp:
            seats_data = await seat_resp.json()

        available_count = sum(1 for s in seats_data if s["status"] == "AVAILABLE")
        held_count = sum(1 for s in seats_data if s["status"] == "HELD")
        confirmed_count = sum(1 for s in seats_data if s["status"] == "CONFIRMED")
        sum_total = available_count + held_count + confirmed_count

        expected_confirmed = 1 + 4 + 1  # 1 from Phase 1 (A12), 4 from Phase 2 (B1..B4), 1 from Phase 3 (C1)
        recon_pass = (sum_total == total_seats) and (confirmed_count == expected_confirmed)

        summary["Phase 5: Reconciliation Invariant"] = {
            "Available": available_count,
            "Held": held_count,
            "Confirmed": confirmed_count,
            "Total Invariant": f"{available_count} + {held_count} + {confirmed_count} = {sum_total} / {total_seats}",
            "Pass": recon_pass
        }

        # -------------------------------------------------------------
        # Outcome Summary Table
        # -------------------------------------------------------------
        print(f"\n{BOLD}{CYAN}======================================================================{RESET}")
        print(f"{BOLD}{CYAN}                    BURST RECONCILIATION REPORT                       {RESET}")
        print(f"{BOLD}{CYAN}======================================================================{RESET}")
        print(f"{'PHASE':<38} | {'OUTCOME':<18} | {'STATUS'}")
        print("-" * 70)

        all_passed = True
        for phase, data in summary.items():
            is_pass = data["Pass"]
            status_badge = f"{GREEN}PASS{RESET}" if is_pass else f"{RED}FAIL{RESET}"
            if not is_pass:
                all_passed = False

            if "Expected" in data:
                detail = f"{data.get('201', 0)}x201, {data.get('409', data.get('Status', 0))}x409"
                if "200" in data:
                    detail += f", {data['200']}x200"
            else:
                detail = f"{data['Confirmed']} confirmed"

            print(f"{phase:<38} | {detail:<18} | {status_badge}")

        print("-" * 70)
        print(f"Reconciliation Invariant: {available_count} (avail) + {held_count} (held) + {confirmed_count} (conf) == {total_seats} total")
        print(f"{BOLD}{CYAN}======================================================================{RESET}")

        if all_passed:
            print(f"\n{BOLD}{GREEN}>>> OVERALL RESULT: ALL PHASES PASSED WITH ZERO 5xx ERRORS <<<{RESET}\n")
            sys.exit(0)
        else:
            print(f"\n{BOLD}{RED}>>> OVERALL RESULT: CONCURRENCY RECONCILIATION FAILED <<<{RESET}\n")
            sys.exit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Paytm Money Burst Concurrency Script")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Base URL of application under test")
    args = parser.parse_args()
    asyncio.run(run_burst_test(args.base_url.rstrip("/")))
