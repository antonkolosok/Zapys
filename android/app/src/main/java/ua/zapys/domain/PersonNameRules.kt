package ua.zapys.domain

object PersonNameRules {
 fun length(raw: String): Int = raw.trim().let { it.codePointCount(0,it.length) }
 fun firstNameError(raw: String): String? = when {
  raw.isBlank() -> "Вкажіть ім’я"
  length(raw)>Limits.MAX_PERSON_NAME -> "Ім’я: максимум ${Limits.MAX_PERSON_NAME} символів"
  else -> null
 }
 fun surnameError(raw: String): String? = if(length(raw)>Limits.MAX_PERSON_NAME) "Прізвище: максимум ${Limits.MAX_PERSON_NAME} символів" else null
 fun patronymicError(raw: String): String? = if(length(raw)>Limits.MAX_PERSON_NAME) "По батькові: максимум ${Limits.MAX_PERSON_NAME} символів" else null
}
