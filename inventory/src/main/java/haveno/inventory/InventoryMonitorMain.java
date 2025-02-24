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

package haveno.inventory;


import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import haveno.common.UserThread;
import haveno.common.app.AsciiLogo;
import haveno.common.app.Log;
import haveno.common.app.Version;
import haveno.common.config.BaseCurrencyNetwork;
import haveno.common.util.Utilities;
import haveno.core.locale.Res;
import lombok.extern.slf4j.Slf4j;
import sun.misc.Signal;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
public class InventoryMonitorMain {

    private static InventoryMonitor inventoryMonitor;
    private static boolean stopped;

    // prog args for regtest: 10 1 XMR_STAGENET
    public static void main(String[] args) {
        // Default values
        int intervalSec = 120;
        boolean useLocalhostForP2P = false;
        BaseCurrencyNetwork network = BaseCurrencyNetwork.XMR_MAINNET;
        int port = 80;

        if (args.length > 0) {
            intervalSec = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            useLocalhostForP2P = args[1].equals("1");
        }
        if (args.length > 2) {
            network = BaseCurrencyNetwork.valueOf(args[2]);
        }
        if (args.length > 3) {
            port = Integer.parseInt(args[3]);
        }

        String appName = "haveno-inventory-monitor-" + network;
        File appDir = new File(Utilities.getUserDataDir(), appName);
        if (!appDir.exists() && !appDir.mkdir()) {
            log.warn("make appDir failed");
        }
        inventoryMonitor = new InventoryMonitor(appDir, useLocalhostForP2P, network, intervalSec, port);

        setup(network, appDir);

        // We shutdown after 5 days to avoid potential memory leak issue.
        // The start script will restart the app.
        UserThread.runAfter(InventoryMonitorMain::shutDown, TimeUnit.DAYS.toSeconds(5));
    }

    private static void setup(BaseCurrencyNetwork network, File appDir) {
        String logPath = Paths.get(appDir.getPath(), "haveno").toString();
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        AsciiLogo.showAsciiLogo();
        Version.setBaseCryptoNetworkId(network.ordinal());

        Res.setup(); // Used for some formatting in the webserver

        // We do not set any capabilities as we don't want to receive any network data beside our response.
        // We also do not use capabilities for the request/response messages as we only connect to seeds nodes and

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(inventoryMonitor.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        Signal.handle(new Signal("INT"), signal -> {
            UserThread.execute(InventoryMonitorMain::shutDown);
        });

        Signal.handle(new Signal("TERM"), signal -> {
            UserThread.execute(InventoryMonitorMain::shutDown);
        });
        keepRunning();
    }

    private static void shutDown() {
        stopped = true;
        inventoryMonitor.shutDown(() -> {
            System.exit(0);
        });
    }

    private static void keepRunning() {
        while (!stopped) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
