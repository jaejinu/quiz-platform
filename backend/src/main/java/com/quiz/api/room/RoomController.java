package com.quiz.api.room;

import com.quiz.api.dto.CreateRoomRequest;
import com.quiz.api.dto.RoomResponse;
import com.quiz.domain.room.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        log.debug("POST /api/rooms title={}", request.title());
        Long id = roomService.create(request);
        RoomResponse response = roomService.findById(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable Long id) {
        log.debug("GET /api/rooms/{}", id);
        return roomService.findById(id);
    }

    @GetMapping("/code/{code}")
    public RoomResponse getByCode(@PathVariable String code) {
        log.debug("GET /api/rooms/code/{}", code);
        return roomService.findByCode(code);
    }
}
