# Contract Test

현재 계약 검증은 `scripts/verify_repository.py`, Java JUnit, `tests/python/test_contracts.py`와 `tests/python/test_domain_safety.py`가 담당한다.

`contracts/domain/state-transitions.csv`의 상태 전이 집합은 Java와 Python 구현 양쪽에서 일치 여부를 검사한다.

Avro/Protobuf와 공식 외부 API Contract Test는 형식·공급자 결정 이후 추가한다.
