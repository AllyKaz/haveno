/*
 * This file is part of Haveno.
 *
 * Haveno is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Haveno is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Haveno. If not, see <http://www.gnu.org/licenses/>.
 */

package haveno.core.payment;

import haveno.core.api.model.PaymentAccountFormField;
import haveno.core.locale.FiatCurrency;
import haveno.core.locale.TradeCurrency;
import haveno.core.payment.payload.PaymentAccountPayload;
import haveno.core.payment.payload.PaymentMethod;
import haveno.core.payment.payload.PromptPayAccountPayload;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
public final class PromptPayAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("THB"));

    public PromptPayAccount() {
        super(PaymentMethod.PROMPT_PAY);
        setSingleTradeCurrency(SUPPORTED_CURRENCIES.get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new PromptPayAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    @Override
    public @NonNull List<PaymentAccountFormField.FieldId> getInputFieldIds() {
        throw new RuntimeException("Not implemented");
    }

    public void setPromptPayId(String promptPayId) {
        ((PromptPayAccountPayload) paymentAccountPayload).setPromptPayId(promptPayId);
    }

    public String getPromptPayId() {
        return ((PromptPayAccountPayload) paymentAccountPayload).getPromptPayId();
    }
}
