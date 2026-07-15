package com.eventledger.gateway.controller;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.service.EventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/events")
public class EventController {
 private final EventService service;
 public EventController(EventService service){this.service=service;}
 @PostMapping
 public EventEntity create(@RequestBody @Valid EventRequest request){
   return service.create(request);
 }
}