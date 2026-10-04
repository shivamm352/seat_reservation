package com.paytmmoney.ticketbooking.service;

import com.paytmmoney.ticketbooking.dto.CreateShowRequest;
import com.paytmmoney.ticketbooking.dto.SeatDto;
import com.paytmmoney.ticketbooking.dto.ShowResponse;

import java.util.List;
import java.util.UUID;

public interface ShowService {
    ShowResponse createShow(CreateShowRequest request);
    ShowResponse getShow(UUID id);
    List<SeatDto> getShowSeats(UUID showId);
}
