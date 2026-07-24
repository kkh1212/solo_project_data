# Python 공통 계약

`CONFIRMED` 금융·시간·식별자·상태·이벤트·외부 주문 제안 불변식의 Python
표현을 제공한다.

- 금액은 `Decimal`만 허용한다.
- 가격·수량은 양수, 비율은 반올림 전후 모두 0~1 범위를 검증한다.
- 수집·생성 시각은 UTC aware `datetime`만 허용한다.
- 거래일과 거래소 시간대를 원본·수집 시각과 별도로 보존한다.
- `UNKNOWN` 주문과 Reservation의 위험한 상태 전이를 거절한다.
- 외부 정책 버전·근거 SHA-256·계좌 별칭·만료와 미국주식 주문 형태를 검증한다.
- 외부 주문 기능과 공급자 SDK는 포함하지 않는다.
- 이벤트 wire format은 `TBD`이며 이 패키지의 dataclass를 Avro 또는 Protobuf 확정으로 해석하지 않는다.

현재는 제3자 의존성이 없고 `PYTHONPATH=libs/python-contracts/src`로 사용한다. 패키징·배포 방식은 실제 소비자가 생길 때 확정한다.
