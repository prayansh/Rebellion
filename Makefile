.PHONY: image
image:
	docker build -t pray-coup-game .

.PHONY: up
up:
	docker-compose up -d

.PHONY: down
down:
	docker-compose down

