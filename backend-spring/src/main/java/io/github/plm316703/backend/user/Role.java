package io.github.plm316703.backend.user;


/**
 * 회원의 역할(권한 종류).
 *
 * enum(열거형)을 쓰는 이유:
 *  - 문자열("PATIENT")로 관리하면 "PATEINT" 같은 오타가 들어가도 컴파일러가 못 잡아냄
 *  - enum은 정해진 값 외에는 들어갈 수 없어서 오타를 컴파일 단계에서 막아줌
 */

public enum Role{
    PATIENT,            //환자 : 로봇팔을 직접 조작하는 사용자
    GUARDIAN,           //보호자 : 연결된 환자의 상태와 기록을 확인
    RESEARCHER          //연구자 : 연구에 동의한 환자의 데이터를 익명 또는 배정 방식
}
