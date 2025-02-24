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

package haveno.core.provider.price;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.TestCase.assertTrue;

@Ignore
public class MarketPriceFeedServiceTest {
    private static final Logger log = LoggerFactory.getLogger(MarketPriceFeedServiceTest.class);

    @Test
    public void testGetPrice() throws InterruptedException {
        PriceFeedService priceFeedService = new PriceFeedService(null, null, null);
        priceFeedService.setCurrencyCode("EUR");
        priceFeedService.startRequestingPrices(tradeCurrency -> {
                    log.debug(tradeCurrency.toString());
                    assertTrue(true);
                },
                (errorMessage, throwable) -> {
                    log.debug(errorMessage);
                    assertTrue(false);
                }
        );
        Thread.sleep(10000);
    }
}
