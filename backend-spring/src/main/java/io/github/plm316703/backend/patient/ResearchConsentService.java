package io.github.plm316703.backend.patient;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import lombok.RequiredArgsConstructor;

/**
 * 연구 동의 철회 서비스 (주방장)
 * "환자가 연구 참여를 그만둔다"는 주문을 받아서,
 * 동의를 철회하고 관련된 연구자 배정을 전부 지운다.
 */
@Service
@RequiredArgsConstructor
public class ResearchConsentService {

    private final PatientRepository patientRepository;  //환자 창고
    private final ResearcherAssignmentRepository researcherAssignmentRepository; //배정 창고

    @Transactional
    public Long withdraw(Long patientId){
        //환자가 존재하나?
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자입니다. id=" + patientId));
        

        //환자가 연구에 동의했나?
        if(patient.getConsentedAt() == null){
            throw new IllegalStateException("동의한 적 없는 환자는 철회할 수 없습니다");
        }

        //동의 철회( JAP가 변경을 알아서 감지해서 저장하므로, save()를 따로 부를 필요 없읍)
        patient.withdrawConsent();


        long deletedCount = researcherAssignmentRepository.deleteAllByPatient(patient);

        return deletedCount;


    }
}


