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

package haveno.network.p2p.network;

import haveno.common.UserThread;
import haveno.common.proto.network.NetworkProtoResolver;
import haveno.network.p2p.NodeAddress;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

// Run in UserThread
public class LocalhostNetworkNode extends NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(LocalhostNetworkNode.class);

    private static int simulateTorDelayTorNode = 500;
    private static int simulateTorDelayHiddenService = 500;

    public static void setSimulateTorDelayTorNode(int simulateTorDelayTorNode) {
        LocalhostNetworkNode.simulateTorDelayTorNode = simulateTorDelayTorNode;
    }

    public static void setSimulateTorDelayHiddenService(int simulateTorDelayHiddenService) {
        LocalhostNetworkNode.simulateTorDelayHiddenService = simulateTorDelayHiddenService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LocalhostNetworkNode(int port,
                                NetworkProtoResolver networkProtoResolver,
                                @Nullable NetworkFilter networkFilter) {
        super(port, networkProtoResolver, networkFilter);
    }

    @Override
    public void start(@Nullable SetupListener setupListener) {
        if (setupListener != null)
            addSetupListener(setupListener);

        createExecutorService();

        // simulate tor connection delay
        UserThread.runAfter(() -> {
            nodeAddressProperty.set(new NodeAddress("localhost", servicePort));

            setupListeners.stream().forEach(SetupListener::onTorNodeReady);

            // simulate tor HS publishing delay
            UserThread.runAfter(() -> {
                try {
                    startServer(new ServerSocket(servicePort));
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Exception at startServer: " + e.getMessage());
                }
                setupListeners.stream().forEach(SetupListener::onHiddenServicePublished);
            }, simulateTorDelayTorNode, TimeUnit.MILLISECONDS);
        }, simulateTorDelayHiddenService, TimeUnit.MILLISECONDS);
    }

    // Called from NetworkNode thread
    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        return new Socket(peerNodeAddress.getHostName(), peerNodeAddress.getPort());
    }
}
