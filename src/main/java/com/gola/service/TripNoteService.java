package com.gola.service;

import com.gola.entity.TripNote;
import com.gola.exception.GolaException;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripNoteService {

    private final TripNoteRepository tripNoteRepo;
    private final TripMemberRepository memberRepo;

    @Transactional
    public TripNote addNote(UUID tripId, UUID authorId, String content) {
        if (!memberRepo.existsByTripIdAndUserId(tripId, authorId)) {
            throw GolaException.forbidden();
        }

        TripNote note = TripNote.builder()
                .tripId(tripId)
                .authorId(authorId)
                .content(content)
                .build();
        
        return tripNoteRepo.save(note);
    }

    public List<TripNote> getNotes(UUID tripId, UUID userId) {
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        return tripNoteRepo.findByTripIdOrderByCreatedAtDesc(tripId);
    }

    @Transactional
    public void deleteNote(UUID noteId, UUID userId) {
        TripNote note = tripNoteRepo.findById(noteId)
                .orElseThrow(() -> GolaException.notFound("Trip note"));

        if (!note.getAuthorId().equals(userId)) {
            throw GolaException.forbidden();
        }

        tripNoteRepo.delete(note);
    }
}
