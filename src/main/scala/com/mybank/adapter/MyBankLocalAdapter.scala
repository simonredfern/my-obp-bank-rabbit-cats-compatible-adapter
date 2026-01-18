/*
 * MyBank Local Adapter
 *
 * This adapter implements the LocalAdapter interface from OBP-Rabbit-Cats-Adapter.
 * It provides mock/stub data for development and testing.
 *
 * To connect to a real CBS, replace the mock implementations with actual
 * HTTP/SOAP/Database calls to your Core Banking System.
 */

package com.mybank.adapter

import cats.effect.IO
import com.tesobe.obp.adapter.interfaces._
import com.tesobe.obp.adapter.models._
import com.tesobe.obp.adapter.telemetry.Telemetry
import io.circe._

class MyBankLocalAdapter(telemetry: Telemetry) extends LocalAdapter {

  override def name: String = "MyBank-Local-Adapter"
  override def version: String = "1.0.0"

  // Mock data for the bank
  private val bankId = "mybank-01"
  private val bankName = "My Bank"
  private val bankFullName = "My Bank Ltd"
  private val defaultCurrency = "EUR"

  override def handleMessage(
    process: String,
    data: JsonObject,
    callContext: CallContext
  ): IO[LocalAdapterResult] = {

    telemetry.debug(s"Handling message: $process", Some(callContext.correlationId)) *>
    (process match {
      case "obp.getAdapterInfo" => getAdapterInfo(callContext)
      case "obp.getBank" => getBank(data, callContext)
      case "obp.getBanks" => getBanks(callContext)
      case "obp.getBankAccount" => getBankAccount(data, callContext)
      case "obp.getBankAccounts" => getBankAccounts(data, callContext)
      case "obp.getTransaction" => getTransaction(data, callContext)
      case "obp.getTransactions" => getTransactions(data, callContext)
      case "obp.checkFundsAvailable" => checkFundsAvailable(data, callContext)
      case "obp.makePayment" => makePayment(data, callContext)
      case _ => handleUnsupported(process, callContext)
    })
  }

  override def checkHealth(callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.debug("Health check requested", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "status" -> Json.fromString("healthy"),
          "message" -> Json.fromString(s"$bankName local adapter is operational"),
          "adapter" -> Json.fromString(name),
          "version" -> Json.fromString(version),
          "timestamp" -> Json.fromLong(System.currentTimeMillis())
        )
      )
    )
  }

  override def getAdapterInfo(callContext: CallContext): IO[LocalAdapterResult] = {
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "name" -> Json.fromString("MyBank-OBP-Adapter"),
          "version" -> Json.fromString("1.0.0-SNAPSHOT"),
          "description" -> Json.fromString(s"OBP Adapter for $bankFullName"),
          "adapter" -> Json.fromString(name),
          "adapter_version" -> Json.fromString(version),
          "bank_id" -> Json.fromString(bankId),
          "bank_name" -> Json.fromString(bankName),
          "supported_operations" -> Json.arr(
            Json.fromString("obp.getBank"),
            Json.fromString("obp.getBanks"),
            Json.fromString("obp.getBankAccount"),
            Json.fromString("obp.getBankAccounts"),
            Json.fromString("obp.getTransaction"),
            Json.fromString("obp.getTransactions"),
            Json.fromString("obp.checkFundsAvailable"),
            Json.fromString("obp.makePayment")
          )
        )
      )
    )
  }

  // ==================== BANK OPERATIONS ====================

  private def getBank(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val requestedBankId = data("bankId").flatMap(_.asString).getOrElse(bankId)

    telemetry.debug(s"Getting bank: $requestedBankId", Some(callContext.correlationId)) *>
    IO.pure(
      if (requestedBankId == bankId) {
        LocalAdapterResult.success(
          JsonObject(
            "bankId" -> Json.fromString(bankId),
            "shortName" -> Json.fromString(bankName),
            "fullName" -> Json.fromString(bankFullName),
            "logoUrl" -> Json.fromString("https://static.openbankproject.com/images/sandbox/bank_x.png"),
            "websiteUrl" -> Json.fromString("https://www.mybank.example.com"),
            "bankRoutingScheme" -> Json.fromString("BIC"),
            "bankRoutingAddress" -> Json.fromString("MYBKUS33XXX")
          )
        )
      } else {
        LocalAdapterResult.error(
          code = "OBP-30001",
          message = s"Bank not found: $requestedBankId"
        )
      }
    )
  }

  private def getBanks(callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.debug("Getting all banks", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "banks" -> Json.arr(
            Json.obj(
              "bankId" -> Json.fromString(bankId),
              "shortName" -> Json.fromString(bankName),
              "fullName" -> Json.fromString(bankFullName),
              "logoUrl" -> Json.fromString("https://static.openbankproject.com/images/sandbox/bank_x.png"),
              "websiteUrl" -> Json.fromString("https://www.mybank.example.com")
            )
          )
        )
      )
    )
  }

  // ==================== ACCOUNT OPERATIONS ====================

  private def getBankAccount(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val requestedBankId = data("bankId").flatMap(_.asString).getOrElse(bankId)
    val accountId = data("accountId").flatMap(_.asString).getOrElse("unknown")

    telemetry.debug(s"Getting account: $accountId at bank: $requestedBankId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "bankId" -> Json.fromString(requestedBankId),
          "accountId" -> Json.fromString(accountId),
          "accountType" -> Json.fromString("CURRENT"),
          "accountRoutings" -> Json.arr(
            Json.obj(
              "scheme" -> Json.fromString("IBAN"),
              "address" -> Json.fromString(s"GB82MYBK${accountId.take(14).padTo(14, '0')}")
            )
          ),
          "branchId" -> Json.fromString("branch-001"),
          "label" -> Json.fromString("Main Account"),
          "currency" -> Json.fromString(defaultCurrency),
          "balance" -> Json.obj(
            "currency" -> Json.fromString(defaultCurrency),
            "amount" -> Json.fromString("10500.75")
          )
        )
      )
    )
  }

  private def getBankAccounts(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val requestedBankId = data("bankId").flatMap(_.asString).getOrElse(bankId)

    telemetry.debug(s"Getting accounts for bank: $requestedBankId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "accounts" -> Json.arr(
            Json.obj(
              "bankId" -> Json.fromString(requestedBankId),
              "accountId" -> Json.fromString("account-001"),
              "accountType" -> Json.fromString("CURRENT"),
              "label" -> Json.fromString("Main Checking Account"),
              "currency" -> Json.fromString(defaultCurrency),
              "balance" -> Json.obj(
                "currency" -> Json.fromString(defaultCurrency),
                "amount" -> Json.fromString("10500.75")
              )
            ),
            Json.obj(
              "bankId" -> Json.fromString(requestedBankId),
              "accountId" -> Json.fromString("account-002"),
              "accountType" -> Json.fromString("SAVINGS"),
              "label" -> Json.fromString("Savings Account"),
              "currency" -> Json.fromString(defaultCurrency),
              "balance" -> Json.obj(
                "currency" -> Json.fromString(defaultCurrency),
                "amount" -> Json.fromString("25000.00")
              )
            )
          )
        )
      )
    )
  }

  // ==================== TRANSACTION OPERATIONS ====================

  private def getTransaction(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val transactionId = data("transactionId").flatMap(_.asString).getOrElse("unknown")
    val accountId = data("accountId").flatMap(_.asString).getOrElse("account-001")

    telemetry.debug(s"Getting transaction: $transactionId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "transactionId" -> Json.fromString(transactionId),
          "accountId" -> Json.fromString(accountId),
          "bankId" -> Json.fromString(bankId),
          "amount" -> Json.fromString("-50.00"),
          "currency" -> Json.fromString(defaultCurrency),
          "description" -> Json.fromString("Payment to merchant"),
          "posted" -> Json.fromString("2025-01-15T10:30:00Z"),
          "completed" -> Json.fromString("2025-01-15T10:30:00Z"),
          "newBalance" -> Json.fromString("10450.75"),
          "type" -> Json.fromString("DEBIT"),
          "counterparty" -> Json.obj(
            "name" -> Json.fromString("Example Merchant"),
            "accountNumber" -> Json.fromString("GB33BUKB20201555555555")
          )
        )
      )
    )
  }

  private def getTransactions(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val accountId = data("accountId").flatMap(_.asString).getOrElse("account-001")

    telemetry.debug(s"Getting transactions for account: $accountId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "transactions" -> Json.arr(
            Json.obj(
              "transactionId" -> Json.fromString("tx-001"),
              "accountId" -> Json.fromString(accountId),
              "bankId" -> Json.fromString(bankId),
              "amount" -> Json.fromString("-50.00"),
              "currency" -> Json.fromString(defaultCurrency),
              "description" -> Json.fromString("Payment to merchant"),
              "posted" -> Json.fromString("2025-01-15T10:30:00Z"),
              "type" -> Json.fromString("DEBIT")
            ),
            Json.obj(
              "transactionId" -> Json.fromString("tx-002"),
              "accountId" -> Json.fromString(accountId),
              "bankId" -> Json.fromString(bankId),
              "amount" -> Json.fromString("2500.00"),
              "currency" -> Json.fromString(defaultCurrency),
              "description" -> Json.fromString("Salary payment"),
              "posted" -> Json.fromString("2025-01-14T09:00:00Z"),
              "type" -> Json.fromString("CREDIT")
            ),
            Json.obj(
              "transactionId" -> Json.fromString("tx-003"),
              "accountId" -> Json.fromString(accountId),
              "bankId" -> Json.fromString(bankId),
              "amount" -> Json.fromString("-120.50"),
              "currency" -> Json.fromString(defaultCurrency),
              "description" -> Json.fromString("Utility bill payment"),
              "posted" -> Json.fromString("2025-01-13T15:45:00Z"),
              "type" -> Json.fromString("DEBIT")
            )
          )
        )
      )
    )
  }

  // ==================== PAYMENT OPERATIONS ====================

  private def checkFundsAvailable(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val amount = data("amount").flatMap(_.asString).getOrElse("0")
    val currency = data("currency").flatMap(_.asString).getOrElse(defaultCurrency)

    telemetry.debug(s"Checking funds: $amount $currency", Some(callContext.correlationId)) *>
    IO.pure {
      val requestedAmount = BigDecimal(amount)
      val availableBalance = BigDecimal("10500.75")
      val fundsAvailable = requestedAmount <= availableBalance

      LocalAdapterResult.success(
        JsonObject(
          "available" -> Json.fromBoolean(fundsAvailable),
          "amount" -> Json.fromString(amount),
          "currency" -> Json.fromString(currency),
          "availableBalance" -> Json.fromString(availableBalance.toString()),
          "message" -> Json.fromString(
            if (fundsAvailable) "Sufficient funds available"
            else "Insufficient funds"
          )
        )
      )
    }
  }

  private def makePayment(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val amount = data("amount").flatMap(_.asString).getOrElse("0")
    val currency = data("currency").flatMap(_.asString).getOrElse(defaultCurrency)
    val description = data("description").flatMap(_.asString).getOrElse("Payment")

    telemetry.debug(s"Making payment: $amount $currency", Some(callContext.correlationId)) *>
    telemetry.recordPaymentSuccess(
      bankId = bankId,
      amount = BigDecimal(amount),
      currency = currency,
      correlationId = callContext.correlationId
    ) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "transactionId" -> Json.fromString(s"tx-${System.currentTimeMillis()}"),
          "amount" -> Json.fromString(amount),
          "currency" -> Json.fromString(currency),
          "description" -> Json.fromString(description),
          "status" -> Json.fromString("COMPLETED"),
          "posted" -> Json.fromString(java.time.Instant.now().toString),
          "bankId" -> Json.fromString(bankId)
        ),
        List(
          BackendMessage(
            source = name,
            status = "success",
            errorCode = "",
            text = "Payment processed successfully",
            duration = Some("0.045")
          )
        )
      )
    )
  }

  // ==================== ERROR HANDLING ====================

  private def handleUnsupported(process: String, callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.warn(s"Unsupported message type: $process", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.error(
        code = "OBP-50000",
        message = s"Message type not implemented: $process",
        messages = List(
          BackendMessage(
            source = name,
            status = "error",
            errorCode = "NOT_IMPLEMENTED",
            text = s"Message type $process is not implemented in $name",
            duration = None
          )
        )
      )
    )
  }
}

object MyBankLocalAdapter {
  def apply(telemetry: Telemetry): MyBankLocalAdapter = new MyBankLocalAdapter(telemetry)
}
