package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GovernmentService {
    private final GovernmentRepository repository;
    public GovernmentService(GovernmentRepository repository){this.repository=repository;}
    public MandateView mandate(String id) {
        if(!"2026-2031".equals(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return repository.published(SubjectType.MANDATE,id,MandateView.class).orElseGet(()->GovernmentViews.pending(id));
    }
}
