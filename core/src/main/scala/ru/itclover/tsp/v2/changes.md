Изменения относительно старой версиии

- В CouplePattern если одна из сторон не возвращает результата, дальнейшее вычисление не производится. В прежней версии в если одна из сторон была Failure, другая сторона могла и не считаться
- В AndThenPattern вторая часть раньше могла идти сразу после первой. То есть вычислялось значение левой части на ивенте, если оно возвращало успех, то пробовалось вычислить правую часть на том же самом сообщении. сейчас это изменилось.