package dev.maboullaite.fhemni.government;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.List;
import org.springframework.stereotype.Repository;
@Repository
public class MeasureRepository {
    private final GovernmentRepository revisions;
    public MeasureRepository(GovernmentRepository revisions){this.revisions=revisions;}
    public List<MeasureView> published(String mandate){return revisions.publishedPayloads(mandate,SubjectType.MEASURE).stream().map(json->revisions.decode(json,MeasureView.class)).toList();}
}
