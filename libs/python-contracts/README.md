# Python 공통 계약

`CONFIRMED` 금융·시간·이벤트 불변식의 Python 표현을 제공한다.

- 금액은 `Decimal`만 허용한다.
- 수집·생성 시각은 UTC aware `datetime`만 허용한다.
- 외부 주문 기능과 공급자 SDK는 포함하지 않는다.
- 이벤트 wire format은 `TBD`이며 이 패키지의 dataclass를 Avro 또는 Protobuf 확정으로 해석하지 않는다.

현재는 제3자 의존성이 없고 `PYTHONPATH=libs/python-contracts/src`로 사용한다. 패키징·배포 방식은 실제 소비자가 생길 때 확정한다.
