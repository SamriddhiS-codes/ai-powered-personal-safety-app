package com.ascenddevs.safetyapp.controller;

import com.ascenddevs.safetyapp.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Lets the SOS screen show whether the trusted contacts were emailed. */
@RestController
@RequestMapping("/api/alerts/{id}/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationService.Result> list(@PathVariable UUID id) {
        return notifications.resultsFor(id);
    }
}
