# ==========================================================
# Python 재생기 이미지
# 저장된 판별 모델(models/)로 공개 뇌파 데이터(BCI IV 2a)를 판별하고
# 신호 품질과 함께 WebSocket으로 Spring 관문에 보낸다 (규격 버전 2).
# ==========================================================
FROM python:3.11-slim

WORKDIR /app

# 화면 출력이 버퍼에 쌓이지 않고 바로 docker logs 에 보이게 한다
ENV PYTHONUNBUFFERED=1

# (1) 패키지 목록만 먼저 복사 → 코드만 바뀌면 설치 단계는 캐시 재사용
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# (2) 실행에 필요한 것만 복사 (classifier 실험 파일은 이미지에 넣지 않음)
COPY models ./models
COPY realtime ./realtime

# 뇌파 데이터셋은 처음 실행 때 인터넷에서 내려받아 /root/mne_data 에 저장된다.
# docker-compose.yml 에서 이 폴더를 볼륨으로 연결해 두 번째부터는 다시 받지 않는다.
# 오류 주입 예: docker compose run --rm replayer python realtime/replayer.py --scenario electrode
CMD ["python", "realtime/replayer.py"]
