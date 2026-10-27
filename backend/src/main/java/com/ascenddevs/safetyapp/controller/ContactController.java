package com.ascenddevs.safetyapp.controller;

import com.ascenddevs.safetyapp.dto.ContactRequest;
import com.ascenddevs.safetyapp.dto.ContactResponse;
import com.ascenddevs.safetyapp.entity.TrustedContact;
import com.ascenddevs.safetyapp.repository.TrustedContactRepository;
import com.ascenddevs.safetyapp.repository.UserRepository;
import com.ascenddevs.safetyapp.service.ContactValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Manages a user's trusted contacts. Until login exists, the user is identified by the
 * userId parameter, the same way the rest of the API currently does it.
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final TrustedContactRepository contacts;
    private final UserRepository users;

    public ContactController(TrustedContactRepository contacts, UserRepository users) {
        this.contacts = contacts;
        this.users = users;
    }

    @GetMapping
    public List<ContactResponse> list(@RequestParam UUID userId) {
        return contacts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(ContactResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ContactResponse> add(@RequestBody ContactRequest req) {
        if (req.userId() == null || !users.existsById(req.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user.");
        }
        String error = ContactValidator.validate(req.name(), req.phone(), req.email());
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
        }
        if (contacts.countByUserId(req.userId()) >= ContactValidator.MAX_CONTACTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can keep up to " + ContactValidator.MAX_CONTACTS + " trusted contacts.");
        }
        String phone = req.phone() == null || req.phone().isBlank() ? null : ContactValidator.cleanPhone(req.phone());
        String email = req.email() == null || req.email().isBlank() ? null : req.email().trim();
        TrustedContact saved = contacts.save(new TrustedContact(req.userId(), req.name().trim(), phone, email));
        return ResponseEntity.ok(ContactResponse.from(saved));
    }

    /** A contact can only be removed by the user who owns it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam UUID userId) {
        return contacts.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .map(c -> {
                    contacts.delete(c);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
