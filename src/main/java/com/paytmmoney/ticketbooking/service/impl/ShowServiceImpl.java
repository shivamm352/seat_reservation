package com.paytmmoney.ticketbooking.service.impl;

import com.paytmmoney.ticketbooking.dto.CreateShowRequest;
import com.paytmmoney.ticketbooking.dto.SeatDto;
import com.paytmmoney.ticketbooking.dto.ShowResponse;
import com.paytmmoney.ticketbooking.entity.Show;
import com.paytmmoney.ticketbooking.entity.ShowSeat;
import com.paytmmoney.ticketbooking.exception.ShowNotFoundException;
import com.paytmmoney.ticketbooking.repository.ShowRepository;
import com.paytmmoney.ticketbooking.repository.ShowSeatRepository;
import com.paytmmoney.ticketbooking.service.ShowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ShowServiceImpl implements ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowServiceImpl(ShowRepository showRepository, ShowSeatRepository showSeatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Override
    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        if (request.seatNumbers().size() != request.totalSeats()) {
            throw new IllegalArgumentException(
                    "Seat numbers count (" + request.seatNumbers().size() +
                    ") must match totalSeats (" + request.totalSeats() + ")");
        }

        Show show = new Show(
                request.name(),
                request.pricePaise(),
                request.perUserLimit(),
                request.totalSeats()
        );
        Show savedShow = showRepository.save(show);

        List<ShowSeat> seats = request.seatNumbers().stream()
                .map(seatNumber -> new ShowSeat(savedShow, seatNumber))
                .toList();
        List<ShowSeat> savedSeats = showSeatRepository.saveAll(seats);

        return ShowResponse.of(savedShow, savedSeats);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowResponse getShow(UUID id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ShowNotFoundException("Show not found with id: " + id));
        List<ShowSeat> seats = showSeatRepository.findByShowIdOrderBySeatNumberAsc(id);
        return ShowResponse.of(show, seats);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatDto> getShowSeats(UUID showId) {
        if (!showRepository.existsById(showId)) {
            throw new ShowNotFoundException("Show not found with id: " + showId);
        }
        return showSeatRepository.findByShowIdOrderBySeatNumberAsc(showId)
                .stream()
                .map(SeatDto::from)
                .toList();
    }
}
