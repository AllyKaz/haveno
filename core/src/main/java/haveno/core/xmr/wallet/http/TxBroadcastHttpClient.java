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

package haveno.core.xmr.wallet.http;

import haveno.core.trade.txproof.AssetTxProofHttpClient;
import haveno.network.Socks5ProxyProvider;
import haveno.network.http.HttpClientImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class TxBroadcastHttpClient extends HttpClientImpl implements AssetTxProofHttpClient {
    TxBroadcastHttpClient(Socks5ProxyProvider socks5ProxyProvider) {
        super(socks5ProxyProvider);
    }
}
