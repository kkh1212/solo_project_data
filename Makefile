.PHONY: verify test-python test-java test

verify:
	python3 scripts/verify_repository.py

test-python:
	python3 -m unittest discover -s tests/python -p 'test_*.py'

test-java:
	mvn --batch-mode --no-transfer-progress verify

test: verify test-python test-java
