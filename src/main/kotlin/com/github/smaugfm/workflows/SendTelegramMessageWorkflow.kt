package com.github.smaugfm.workflows

import com.elbekD.bot.types.InlineKeyboardButton
import com.elbekD.bot.types.InlineKeyboardMarkup
import com.github.smaugfm.apis.TelegramApi
import com.github.smaugfm.models.MonoStatementItem
import com.github.smaugfm.models.MonoWebHookResponseData
import com.github.smaugfm.models.TransactionUpdateType
import com.github.smaugfm.models.TransactionUpdateType.Companion.buttonText
import com.github.smaugfm.models.TransactionUpdateType.Companion.serialize
import com.github.smaugfm.models.YnabTransactionDetail
import com.github.smaugfm.models.settings.Mappings
import com.github.smaugfm.util.MCC
import com.github.smaugfm.util.formatAmount
import com.github.smaugfm.util.replaceNewLines
import java.util.Currency
import kotlin.reflect.KClass

class SendTelegramMessageWorkflow(val mappings: Mappings, val telegramApi: TelegramApi) {
    suspend operator fun invoke(
        monoResponse: MonoWebHookResponseData,
        transaction: YnabTransactionDetail
    ) {
        val accountCurrency = mappings.getAccountCurrency(monoResponse.account)!!

        val msg = formatHTMLStatementMessage(accountCurrency, monoResponse.statementItem, transaction)
        val markup = formatInlineKeyboard(emptySet())

        telegramApi.sendMessage(
            mappings.getTelegramChatIdAccByMono(monoResponse.account) ?: return, msg, "HTML", markup = markup
        )
    }

    companion object {
        internal fun formatInlineKeyboard(
            pressed: Set<KClass<out TransactionUpdateType>>,
        ): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                listOf(
                    listOf(
                        button<TransactionUpdateType.Uncategorize>(pressed),
                        button<TransactionUpdateType.Unapprove>(pressed),
                    ),
                    listOf(
                        button<TransactionUpdateType.Unknown>(pressed),
                        button<TransactionUpdateType.MakePayee>(pressed),
                    )
                )
            )
        }

        @Suppress("LongParameterList")
        internal fun formatHTMLStatementMessage(
            description: String,
            mcc: String,
            amount: String,
            category: String,
            payee: String,
            id: String,
        ): String {
            val builder = StringBuilder("Новая транзакция Monobank добавлена в YNAB\n")
            return with(Unit) {
                builder.append("\uD83D\uDCB3 <b>$description</b>\n")
                builder.append("      $mcc\n")
                builder.append("      <u>$amount</u>\n")
                builder.append("      <code>Category: $category</code>\n")
                builder.append("      <code>Payee:    $payee</code>\n")
                builder.append("\n\n")
                builder.append("<pre>$id</pre>")

                builder.toString()
            }
        }

        internal fun formatAmountWithCurrency(amount: Long, currency: Currency) =
            currency.formatAmount(amount) + currency.currencyCode

        internal fun formatHTMLStatementMessage(
            accountCurrency: Currency,
            monoStatementItem: MonoStatementItem,
            transaction: YnabTransactionDetail,
        ): String {
            with(monoStatementItem) {
                val accountAmount = formatAmountWithCurrency(amount, accountCurrency)
                val operationAmount = formatAmountWithCurrency(this.operationAmount, currencyCode)
                return formatHTMLStatementMessage(
                    description.replaceNewLines(),
                    (MCC.mapRussian[mcc] ?: "Неизвестный MCC") + " ($mcc)",
                    accountAmount + (if (accountCurrency != currencyCode) " ($operationAmount)" else ""),
                    transaction.category_name ?: "",
                    transaction.payee_name ?: "",
                    transaction.id,
                )
            }
        }

        internal inline fun <reified T : TransactionUpdateType> button(
            pressed: Set<KClass<out TransactionUpdateType>>
        ) =
            with(T::class) {
                InlineKeyboardButton(
                    buttonText<T>(this in pressed), callback_data = serialize<T>()
                )
            }
    }
}
