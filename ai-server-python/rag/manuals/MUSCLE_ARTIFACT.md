# MUSCLE_ARTIFACT — 근육 잡음

> Nuada Airgetlám 점검 매뉴얼 (연구용 프로토타입, 의료기기 매뉴얼 아님). 로봇팔 재개는 관제 화면의 수동 재개로만 한다.
>
> 참고 자료
> - Muthukumaraswamy, S. D. (2013). High-frequency brain activity and muscle artifacts in MEG/EEG: a review and recommendations. Frontiers in Human Neuroscience. https://doi.org/10.3389/fnhum.2013.00138
> - Goncharova, I. I., McFarland, D. J., Vaughan, T. M., Wolpaw, J. R. (2003). EMG contamination of EEG: spectral and topographical characteristics. Clinical Neurophysiology 114, 1580–1593. https://www.neurotechcenter.org/sites/default/files/misc/EMG%20contamination%20of%20EEG%20spectral%20and%20topographical%20characteristics.pdf

## 증상
- 신호 품질이 낮아지고 판별기가 MUSCLE_ARTIFACT 를 보고한다.
- 빠르고 불규칙한 잡음이 여러 채널에 나타나며, 특히 이마·관자놀이 쪽 채널에서 크다. 근육 잡음은 약 20~300Hz의 넓은 대역에 걸치며, 이마 쪽은 20~30Hz, 관자놀이 쪽은 40~80Hz에서 특히 크다 (출처: Muthukumaraswamy 2013; Goncharova 외 2003).
- 이 시스템은 운동 상상 판별에 쓰는 뮤·베타 리듬(8~30Hz)과 겹치지 않도록 40Hz 이상(최대 120Hz)만 보고 근육 잡음을 감지한다.

## 원인
- 턱을 꽉 물기, 이 갈기, 침 삼키기, 말하기.
- 목·어깨 긴장, 불편한 자세, 눈썹 찌푸림.
- 운동 상상 과제를 "실제로 힘을 주는 것"으로 오해함.

## 확인 방법
1. 환자의 표정·턱·목 상태를 관찰한다.
2. 환자에게 지금 불편한 곳이 있는지 묻는다.
3. 잡음이 특정 행동(말하기, 삼키기)과 함께 나타나는지 본다.

## 해결 단계
1. 로봇팔은 이미 정지 상태다. 환자에게 차분히 설명한다.
2. 턱과 어깨의 힘을 빼고 입을 살짝 벌린 편안한 자세를 안내한다.
3. 운동 상상은 "실제로 움직이지 않고 머릿속으로만 떠올리는 것"임을 다시 설명한다.
4. 의자·베개를 조정해 목이 편하게 받쳐지도록 한다.
5. 잠시 휴식 후 신호 품질이 회복되고 연속 정상 데이터가 쌓이면 관제 담당자가 수동 재개를 요청한다.

## 안 될 때
- 피로 신호일 수 있으므로 휴식 시간을 늘리거나 세션을 짧게 끝낸다.
- 반복되면 과제 설명 방식을 연구책임자와 다시 정한다.
- 약한 근육 긴장은 정수리(C3·Cz·C4) 근처에서도 12Hz 이상으로 섞일 수 있어, 40Hz 이상만 보는 감지기에 걸리지 않을 수 있다. 신호 품질은 정상인데 판별이 불안정하면 근육 긴장부터 의심한다 (출처: Goncharova 외 2003).
