package io.github.plm316703.backend.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연구 동의 / 철회 서비스 (주방장)
 * "환자가 연구 참여를 그만둔다"는 주문을 받아서,
 * 동의를 철회하고 관련된 연구자 배정을 전부 지운다.
 */
@Service
@RequiredArgsConstructor
public class ResearchConsentService {

    private final PatientRepository patientRepository;  //환자 창고
    private final ResearcherAssignmentRepository researcherAssignmentRepository; //배정 창고
    private final ApplicationEventPublisher events;     //"철회됨" 소식을 관제(로봇팔)에 알리는 확성기

    /** 철회가 끝났다는 소식. 관제 쪽이 받아서 이 환자의 로봇팔을 즉시 정지시킨다. */
    public record ConsentWithdrawnEvent(Long patientId) {}

    @Transactional
    public Long withdraw(Long patientId){
        //환자가 존재하나?
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자입니다. id=" + patientId));

        //환자가 연구에 동의했나?
        if(patient.getConsentedAt() == null){
            throw new IllegalStateException("동의한 적 없는 환자는 철회할 수 없습니다");
        }
        if(!patient.isResearchConsent()){
            throw new IllegalStateException("이미 동의를 철회한 상태입니다");
        }
        //동의 철회( JAP가 변경을 알아서 감지해서 저장하므로, save()를 따로 부를 필요 없읍)
        patient.withdrawConsent();
        long deletedCount = researcherAssignmentRepository.deleteAllByPatient(patient);

        // 로봇팔 사용 중단: 커밋이 끝난 뒤 관제가 이 소식을 받아 정지시킨다
        events.publishEvent(new ConsentWithdrawnEvent(patientId));
        return deletedCount;
    }

    /**
     * 연구 동의 (다시 동의도 포함).
     * 등록 기록(enrolledAt)이 있으면 동의하는 순간 다시 "참여 중"이 된다.
     */
    @Transactional
    public void giveConsent(Long patientId){
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자입니다. id=" + patientId));
        if(patient.isResearchConsent()){
            throw new IllegalStateException("이미 연구에 동의한 상태입니다");
        }
        patient.giveConsent();
    }
}
