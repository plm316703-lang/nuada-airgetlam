package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연구자배정 서비스 (주방장)
 * "환자를 연구자에게 배정한다"는 주문을 받아서, 규칙을 확인한 뒤 저장을 부탁한다.
 */


@Service
@RequiredArgsConstructor
public class ResearcherAssignmentService {

    private final PatientRepository patientRepository;  //환자 창고
    private final UserRepository userRepository;    //회원 창고
    private final ResearcherAssignmentRepository researcherAssignmentRepository; //배정 창고

    @Transactional
    public Long assign(Long patientId, Long researcherId){
        //환자가 존재하나?
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자입니다. id=" + patientId));
        //연구자 회원이 존재하나
        User researcher = userRepository.findById(researcherId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + researcherId));

        //그 회원이 진짜 연구자 역할인가?
        //enum은 == 로 비교할 수 있음
        if(researcher.getRole() != Role.RESEARCHER){
            throw new IllegalArgumentException("연구자 역할의 회원만 배정받을 수 있습니다.");

        }

        //환자가 연구에 동의했나?
        // !는 "아니다" -> 동의하지 않는다면
        if(!patient.isResearchConsent()){
            throw new IllegalStateException("연구에 동의하지 않은 환자는 배정할 수 없습니다.");
        }

        //이미 배정된 사이인가?(방금 만든 쿼리 메서드 사용)
        if(researcherAssignmentRepository.existsByPatientAndResearcher(patient,researcher)){
            throw new IllegalStateException("이미 이 연구자에게 배정된 환자입니다.");
        }

        //모든 규칙 통과 -> 창고 관리인에게 저장 부탁
        ResearcherAssignment saved = researcherAssignmentRepository.save(
            new ResearcherAssignment(patient, researcher));


        //저장된 배정의 번호(id)를 돌려줌 (나중에 웨이터가 손님에게 전달할 값)
        return saved.getId();


    }
}
