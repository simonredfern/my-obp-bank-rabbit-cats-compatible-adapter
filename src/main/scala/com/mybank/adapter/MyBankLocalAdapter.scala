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

  override def name: String = "MyBank-Local-Adapter-Botswana"
  override def version: String = "1.0.0"

  private val bankId          = "mybank-01"
  private val bankName        = "My Bank"
  private val bankFullName    = "My Bank Ltd"
  private val defaultCurrency = "EUR"

  override def handleMessage(
    process: String,
    data: JsonObject,
    callContext: CallContext
  ): IO[LocalAdapterResult] = {

    telemetry.debug(s"Handling message: $process", Some(callContext.correlationId)) *>
    (process match {
      case "obp.getAdapterInfo" | "obp_get_adapter_info"           => getAdapterInfo(callContext)
      case "obp.getBank" | "obp_get_bank"                          => getBank(data, callContext)
      case "obp.getBanks" | "obp_get_banks"                        => getBanks(callContext)
      case "obp.getBankAccount" | "obp_get_bank_account"           => getBankAccount(data, callContext)
      case "obp.getBankAccounts" | "obp_get_bank_accounts"         => getBankAccounts(data, callContext)
      case "obp.getTransaction" | "obp_get_transaction"            => getTransaction(data, callContext)
      case "obp.getTransactions" | "obp_get_transactions"          => getTransactions(data, callContext)
      case "obp.checkFundsAvailable" | "obp_check_funds_available" => checkFundsAvailable(data, callContext)
      case "obp.makePayment" | "obp_make_payment"                  => makePayment(data, callContext)
      case _                                                        => handleUnsupported(process, callContext)
    })
  }

  override def checkHealth(callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.debug("Health check requested", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "status"    -> Json.fromString("healthy"),
          "message"   -> Json.fromString(s"$bankName local adapter is operational"),
          "adapter"   -> Json.fromString(name),
          "version"   -> Json.fromString(version),
          "timestamp" -> Json.fromLong(System.currentTimeMillis())
        )
      )
    )
  }

  override def getAdapterInfo(callContext: CallContext): IO[LocalAdapterResult] = {
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "name"            -> Json.fromString("MyBank-OBP-Adapter-Marko"),
          "version"         -> Json.fromString("1.0.0-SNAPSHOT"),
          "description"     -> Json.fromString(s"OBP Adapter for $bankFullName"),
          "adapter"         -> Json.fromString(name),
          "adapter_version" -> Json.fromString(version),
          "bank_id"         -> Json.fromString(bankId),
          "bank_name"       -> Json.fromString(bankName)
        )
      )
    )
  }

  // ==================== HELPERS ====================

  // OBP sends ID types as {"value": "..."} objects; fall back to plain string for flexibility
  private def extractId(obj: JsonObject, field: String, default: String = ""): String =
    obj(field)
      .flatMap(j => j.asObject.flatMap(_("value")).flatMap(_.asString).orElse(j.asString))
      .getOrElse(default)

  private def bankId_(v: String): Json = Json.obj("value" -> Json.fromString(v))
  private def accountId_(v: String): Json = Json.obj("value" -> Json.fromString(v))

  private def bankAccountJson(accId: String, accType: String, balance: BigDecimal, number: String, iban: String): Json =
    Json.obj(
      "accountId"       -> accountId_(accId),
      "accountType"     -> Json.fromString(accType),
      "balance"         -> Json.fromBigDecimal(balance),
      "currency"        -> Json.fromString(defaultCurrency),
      "name"            -> Json.fromString(s"$accType Account"),
      "label"           -> Json.fromString(s"$accType Account"),
      "number"          -> Json.fromString(number),
      "bankId"          -> bankId_(bankId),
      "lastUpdate"      -> Json.fromString(java.time.Instant.now().toString),
      "branchId"        -> Json.fromString("branch-001"),
      "accountRoutings" -> Json.arr(
        Json.obj("scheme" -> Json.fromString("IBAN"), "address" -> Json.fromString(iban))
      ),
      "accountRules"    -> Json.arr(),
      "accountHolder"   -> Json.fromString("Account Holder")
    )

  private def transactionJson(
    txId: String, accId: String,
    amount: BigDecimal, balance: BigDecimal,
    txType: String, description: String, date: String
  ): Json =
    Json.obj(
      "uuid"            -> Json.fromString(txId),
      "id"              -> Json.obj("value" -> Json.fromString(txId)),
      "thisAccount"     -> Json.obj(
        "bankId"                -> bankId_(bankId),
        "accountId"             -> accountId_(accId),
        "accountType"           -> Json.fromString("CURRENT"),
        "balance"               -> Json.fromBigDecimal(balance),
        "currency"              -> Json.fromString(defaultCurrency),
        "name"                  -> Json.fromString("Main Checking Account"),
        "lastUpdate"            -> Json.fromString(date),
        "accountHolder"         -> Json.fromString("Account Holder"),
        "label"                 -> Json.fromString("Main Checking Account"),
        "accountRoutingScheme"  -> Json.fromString("IBAN"),
        "accountRoutingAddress" -> Json.fromString("BWP1234567890001"),
        "branchId"              -> Json.fromString("branch-001"),
        "number"                -> Json.fromString("1234567890"),
        "accountRoutings"       -> Json.arr(
          Json.obj("scheme" -> Json.fromString("IBAN"), "address" -> Json.fromString("BWP1234567890001"))
        ),
        "accountRules"          -> Json.arr()
      ),
      "otherAccount"    -> Json.obj(
        "nationalIdentifier"        -> Json.fromString(""),
        "kind"                      -> Json.fromString("PERSONAL"),
        "counterpartyId"            -> Json.fromString("cp-001"),
        "counterpartyName"          -> Json.fromString("Example Counterparty"),
        "thisBankId"                -> bankId_(bankId),
        "thisAccountId"             -> accountId_(accId),
        "otherBankRoutingScheme"    -> Json.fromString("BIC"),
        "otherBankRoutingAddress"   -> Json.Null,
        "otherAccountRoutingScheme" -> Json.fromString("IBAN"),
        "otherAccountRoutingAddress"-> Json.Null,
        "otherAccountProvider"      -> Json.fromString(""),
        "isBeneficiary"             -> Json.fromBoolean(false)
      ),
      "transactionType" -> Json.fromString(txType),
      "amount"          -> Json.fromBigDecimal(amount),
      "currency"        -> Json.fromString(defaultCurrency),
      "description"     -> Json.fromString(description),
      "startDate"       -> Json.fromString(date),
      "finishDate"      -> Json.fromString(date),
      "balance"         -> Json.fromBigDecimal(balance),
      "status"          -> Json.fromString("COMPLETED")
    )

  // ==================== BANK OPERATIONS ====================

  private def getBank(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val reqBankId = extractId(data, "bankId", bankId)

    telemetry.debug(s"Getting bank: $reqBankId", Some(callContext.correlationId)) *>
    IO.pure(
      if (reqBankId == bankId) {
        LocalAdapterResult.success(
          JsonObject(
            "bankId"             -> bankId_(bankId),
            "shortName"          -> Json.fromString(bankName),
            "fullName"           -> Json.fromString(bankFullName),
            "logoUrl"            -> Json.fromString("https://static.openbankproject.com/images/sandbox/bank_x.png"),
            "websiteUrl"         -> Json.fromString("https://www.mybank.example.com"),
            "bankRoutingScheme"  -> Json.fromString("BIC"),
            "bankRoutingAddress" -> Json.fromString("MYBKUS33XXX")
          )
        )
      } else {
        LocalAdapterResult.error(
          code    = "OBP-30001",
          message = s"Bank not found: $reqBankId"
        )
      }
    )
  }

  private def getBanks(callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.debug("Getting all banks", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(
          Json.obj(
            "bankId"             -> bankId_(bankId),
            "shortName"          -> Json.fromString(bankName),
            "fullName"           -> Json.fromString(bankFullName),
            "logoUrl"            -> Json.fromString("https://static.openbankproject.com/images/sandbox/bank_x.png"),
            "websiteUrl"         -> Json.fromString("https://www.mybank.example.com"),
            "bankRoutingScheme"  -> Json.fromString("BIC"),
            "bankRoutingAddress" -> Json.fromString("MYBKUS33XXX")
          )
        ),
        Nil
      )
    )
  }

  // ==================== ACCOUNT OPERATIONS ====================

  private def getBankAccount(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val reqBankId = extractId(data, "bankId", bankId)
    val reqAccId  = extractId(data, "accountId", "account-001")

    telemetry.debug(s"Getting account: $reqAccId at bank: $reqBankId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "accountId"       -> accountId_(reqAccId),
          "accountType"     -> Json.fromString("CURRENT"),
          "balance"         -> Json.fromBigDecimal(BigDecimal("10500.75")),
          "currency"        -> Json.fromString(defaultCurrency),
          "name"            -> Json.fromString("Main Checking Account"),
          "label"           -> Json.fromString("Main Checking Account"),
          "number"          -> Json.fromString("1234567890"),
          "bankId"          -> bankId_(bankId),
          "lastUpdate"      -> Json.fromString(java.time.Instant.now().toString),
          "branchId"        -> Json.fromString("branch-001"),
          "accountRoutings" -> Json.arr(
            Json.obj("scheme" -> Json.fromString("IBAN"), "address" -> Json.fromString("BWP1234567890001"))
          ),
          "accountRules"    -> Json.arr(),
          "accountHolder"   -> Json.fromString("Account Holder")
        )
      )
    )
  }

  private def getBankAccounts(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val bankIdAccountIds = data("bankIdAccountIds").flatMap(_.asArray).getOrElse(Vector.empty)

    telemetry.debug(s"Getting accounts, requested: ${bankIdAccountIds.size}", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(
          bankAccountJson("account-001", "CURRENT", BigDecimal("10500.75"), "1234567890", "BWP1234567890001"),
          bankAccountJson("account-002", "SAVINGS",  BigDecimal("25000.00"), "1234567891", "BWP1234567890002")
        ),
        Nil
      )
    )
  }

  // ==================== TRANSACTION OPERATIONS ====================

  private def getTransaction(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val txId  = extractId(data, "transactionId", "tx-001")
    val accId = extractId(data, "accountId", "account-001")

    telemetry.debug(s"Getting transaction: $txId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        transactionJson(txId, accId, BigDecimal("-50.00"), BigDecimal("10450.75"), "DEBIT", "Payment to merchant", "2025-01-15T10:30:00Z")
          .asObject.getOrElse(JsonObject.empty)
      )
    )
  }

  private def getTransactions(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val accId = extractId(data, "accountId", "account-001")

    telemetry.debug(s"Getting transactions for account: $accId", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.success(
        Json.arr(
          transactionJson("tx-001", accId, BigDecimal("-50.00"),  BigDecimal("10450.75"), "DEBIT",  "Payment to merchant",  "2025-01-15T10:30:00Z"),
          transactionJson("tx-002", accId, BigDecimal("2500.00"), BigDecimal("12950.75"), "CREDIT", "Salary payment",       "2025-01-14T09:00:00Z"),
          transactionJson("tx-003", accId, BigDecimal("-120.50"), BigDecimal("10500.75"), "DEBIT",  "Utility bill payment", "2025-01-13T15:45:00Z")
        ),
        Nil
      )
    )
  }

  // ==================== PAYMENT OPERATIONS ====================

  private def checkFundsAvailable(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val amount   = data("amount").flatMap(_.asString).getOrElse("0")
    val currency = data("currency").flatMap(_.asString).getOrElse(defaultCurrency)

    telemetry.debug(s"Checking funds: $amount $currency", Some(callContext.correlationId)) *>
    IO.pure {
      val requested  = BigDecimal(amount)
      val available  = BigDecimal("10500.75")
      val sufficient = requested <= available

      LocalAdapterResult.success(
        JsonObject(
          "available"        -> Json.fromBoolean(sufficient),
          "amount"           -> Json.fromString(amount),
          "currency"         -> Json.fromString(currency),
          "availableBalance" -> Json.fromString(available.toString())
        )
      )
    }
  }

  private def makePayment(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
    val txId = s"tx-${System.currentTimeMillis()}"

    telemetry.debug(s"Making payment, txId: $txId", Some(callContext.correlationId)) *>
    telemetry.recordPaymentSuccess(
      bankId        = bankId,
      amount        = BigDecimal("0"),
      currency      = defaultCurrency,
      correlationId = callContext.correlationId
    ) *>
    IO.pure(
      // InBoundMakePaymentV400.data is TransactionId(value: String)
      LocalAdapterResult.success(
        JsonObject("value" -> Json.fromString(txId))
      )
    )
  }

  // ==================== ERROR HANDLING ====================

  private def handleUnsupported(process: String, callContext: CallContext): IO[LocalAdapterResult] = {
    telemetry.warn(s"Unsupported message type: $process", Some(callContext.correlationId)) *>
    IO.pure(
      LocalAdapterResult.error(
        code     = "OBP-50000",
        message  = s"Message type not implemented: $process",
        messages = List(
          BackendMessage(
            source    = name,
            status    = "error",
            errorCode = "NOT_IMPLEMENTED",
            text      = s"Message type $process is not implemented in $name",
            duration  = None
          )
        )
      )
    )
  }
}

object MyBankLocalAdapter {
  def apply(telemetry: Telemetry): MyBankLocalAdapter = new MyBankLocalAdapter(telemetry)
}
