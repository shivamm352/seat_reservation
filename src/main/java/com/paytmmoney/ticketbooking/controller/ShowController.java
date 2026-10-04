package com.paytmmoney.ticketbooking.controller;

import com.paytmmoney.ticketbooking.dto.CreateShowRequest;
import com.paytmmoney.ticketbooking.dto.SeatDto;
import com.paytmmoney.ticketbooking.dto.ShowResponse;
import com.paytmmoney.ticketbooking.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
        ShowResponse response = showService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowResponse> getShow(@PathVariable UUID id) {
        ShowResponse response = showService.getShow(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/seats")
    public ResponseEntity<List<SeatDto>> getShowSeats(@PathVariable UUID id) {
        List<SeatDto> seats = showService.getShowSeats(id);
        return ResponseEntity.ok(seats);
    }
}
