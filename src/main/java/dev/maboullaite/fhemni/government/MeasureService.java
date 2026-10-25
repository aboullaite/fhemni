package dev.maboullaite.fhemni.government;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
@Service
public class MeasureService {
    private final MeasureRepository repository;
    public MeasureService(MeasureRepository repository){this.repository=repository;}
    public Page<MeasureView> list(String mandate,int page){
        if(!"2026-2031".equals(mandate))throw new NoSuchElementException("Unknown mandate.");
        if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");
        var rows=repository.published(mandate);int start=Math.min((page-1)*10,rows.size());return new Page<>(rows.subList(start,Math.min(start+10,rows.size())),page,10,rows.size());
    }
    public MeasureView detail(String id){return repository.published("2026-2031").stream().filter(row->row.slug().equals(id)||row.id().toString().equals(id)).findFirst().orElseThrow(()->new NoSuchElementException("Published measure not found."));}
    public static boolean canApply(MeasureView view){return view.operationalStatus()==OperationalStatus.AVAILABLE&&view.eligibility()!=null&&view.steps()!=null&&view.officialActionUrl()!=null&&!view.evidence().isEmpty();}
}
