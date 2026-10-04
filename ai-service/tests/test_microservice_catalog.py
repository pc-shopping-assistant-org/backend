from ai_service.application.use_cases.assistant import AssistantService


def test_catalog_summary_preserves_microservice_image_and_price() -> None:
    card = AssistantService._card({
        "name": "Gaming laptop",
        "mainImageUrl": "https://example.com/laptop.png",
        "minPrice": 20000000,
        "status": "ACTIVE",
    })
    assert card.image_url == "https://example.com/laptop.png"
    assert card.list_price == 20000000
