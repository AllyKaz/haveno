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

package haveno.core.filter;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import haveno.common.crypto.Sig;
import haveno.common.proto.ProtoUtil;
import haveno.common.util.CollectionUtils;
import haveno.common.util.ExtraDataMapValidator;
import haveno.common.util.Utilities;
import haveno.network.p2p.storage.payload.ExpirablePayload;
import haveno.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Value
public final class Filter implements ProtectedStoragePayload, ExpirablePayload {
    public static final long TTL = TimeUnit.DAYS.toMillis(180);

    private final List<String> bannedOfferIds;
    private final List<String> nodeAddressesBannedFromTrading;
    private final List<String> bannedAutoConfExplorers;
    private final List<PaymentAccountFilter> bannedPaymentAccounts;
    private final List<String> bannedCurrencies;
    private final List<String> bannedPaymentMethods;
    private final List<String> arbitrators;
    private final List<String> seedNodes;
    private final List<String> priceRelayNodes;
    private final boolean preventPublicBtcNetwork;
    private final List<String> btcNodes;
    // SignatureAsBase64 is not set initially as we use the serialized data for signing. We set it after signature is
    // created by cloning the object with a non-null sig.
    @Nullable
    private final String signatureAsBase64;
    // The pub EC key from the dev who has signed and published the filter (different to ownerPubKeyBytes)
    private final String signerPubKeyAsHex;

    // The pub key used for the data protection in the p2p storage
    private final byte[] ownerPubKeyBytes;
    private final String disableTradeBelowVersion;
    private final List<String> mediators;
    private final List<String> refundAgents;

    private final List<String> bannedAccountWitnessSignerPubKeys;

    private final List<String> btcFeeReceiverAddresses;

    private final long creationDate;

    private final List<String> bannedPrivilegedDevPubKeys;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    private transient PublicKey ownerPubKey;

    // added at v1.3.8
    private final boolean disableAutoConf;

    // added at v1.5.5
    private final Set<String> nodeAddressesBannedFromNetwork;
    private final boolean disableApi;

    // added at v1.6.0
    private final boolean disableMempoolValidation;

    // After we have created the signature from the filter data we clone it and apply the signature
    static Filter cloneWithSig(Filter filter, String signatureAsBase64) {
        return new Filter(filter.getBannedOfferIds(),
                filter.getNodeAddressesBannedFromTrading(),
                filter.getBannedPaymentAccounts(),
                filter.getBannedCurrencies(),
                filter.getBannedPaymentMethods(),
                filter.getArbitrators(),
                filter.getSeedNodes(),
                filter.getPriceRelayNodes(),
                filter.isPreventPublicBtcNetwork(),
                filter.getBtcNodes(),
                filter.getDisableTradeBelowVersion(),
                filter.getMediators(),
                filter.getRefundAgents(),
                filter.getBannedAccountWitnessSignerPubKeys(),
                filter.getBtcFeeReceiverAddresses(),
                filter.getOwnerPubKeyBytes(),
                filter.getCreationDate(),
                filter.getExtraDataMap(),
                signatureAsBase64,
                filter.getSignerPubKeyAsHex(),
                filter.getBannedPrivilegedDevPubKeys(),
                filter.isDisableAutoConf(),
                filter.getBannedAutoConfExplorers(),
                filter.getNodeAddressesBannedFromNetwork(),
                filter.isDisableMempoolValidation(),
                filter.isDisableApi());
    }

    // Used for signature verification as we created the sig without the signatureAsBase64 field we set it to null again
    static Filter cloneWithoutSig(Filter filter) {
        return new Filter(filter.getBannedOfferIds(),
                filter.getNodeAddressesBannedFromTrading(),
                filter.getBannedPaymentAccounts(),
                filter.getBannedCurrencies(),
                filter.getBannedPaymentMethods(),
                filter.getArbitrators(),
                filter.getSeedNodes(),
                filter.getPriceRelayNodes(),
                filter.isPreventPublicBtcNetwork(),
                filter.getBtcNodes(),
                filter.getDisableTradeBelowVersion(),
                filter.getMediators(),
                filter.getRefundAgents(),
                filter.getBannedAccountWitnessSignerPubKeys(),
                filter.getBtcFeeReceiverAddresses(),
                filter.getOwnerPubKeyBytes(),
                filter.getCreationDate(),
                filter.getExtraDataMap(),
                null,
                filter.getSignerPubKeyAsHex(),
                filter.getBannedPrivilegedDevPubKeys(),
                filter.isDisableAutoConf(),
                filter.getBannedAutoConfExplorers(),
                filter.getNodeAddressesBannedFromNetwork(),
                filter.isDisableMempoolValidation(),
                filter.isDisableApi());
    }

    public Filter(List<String> bannedOfferIds,
                  List<String> nodeAddressesBannedFromTrading,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  List<String> bannedCurrencies,
                  List<String> bannedPaymentMethods,
                  List<String> arbitrators,
                  List<String> seedNodes,
                  List<String> priceRelayNodes,
                  boolean preventPublicBtcNetwork,
                  List<String> btcNodes,
                  String disableTradeBelowVersion,
                  List<String> mediators,
                  List<String> refundAgents,
                  List<String> bannedAccountWitnessSignerPubKeys,
                  List<String> btcFeeReceiverAddresses,
                  PublicKey ownerPubKey,
                  String signerPubKeyAsHex,
                  List<String> bannedPrivilegedDevPubKeys,
                  boolean disableAutoConf,
                  List<String> bannedAutoConfExplorers,
                  Set<String> nodeAddressesBannedFromNetwork,
                  boolean disableMempoolValidation,
                  boolean disableApi) {
        this(bannedOfferIds,
                nodeAddressesBannedFromTrading,
                bannedPaymentAccounts,
                bannedCurrencies,
                bannedPaymentMethods,
                arbitrators,
                seedNodes,
                priceRelayNodes,
                preventPublicBtcNetwork,
                btcNodes,
                disableTradeBelowVersion,
                mediators,
                refundAgents,
                bannedAccountWitnessSignerPubKeys,
                btcFeeReceiverAddresses,
                Sig.getPublicKeyBytes(ownerPubKey),
                System.currentTimeMillis(),
                null,
                null,
                signerPubKeyAsHex,
                bannedPrivilegedDevPubKeys,
                disableAutoConf,
                bannedAutoConfExplorers,
                nodeAddressesBannedFromNetwork,
                disableMempoolValidation,
                disableApi);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    public Filter(List<String> bannedOfferIds,
                  List<String> nodeAddressesBannedFromTrading,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  List<String> bannedCurrencies,
                  List<String> bannedPaymentMethods,
                  List<String> arbitrators,
                  List<String> seedNodes,
                  List<String> priceRelayNodes,
                  boolean preventPublicBtcNetwork,
                  List<String> btcNodes,
                  String disableTradeBelowVersion,
                  List<String> mediators,
                  List<String> refundAgents,
                  List<String> bannedAccountWitnessSignerPubKeys,
                  List<String> btcFeeReceiverAddresses,
                  byte[] ownerPubKeyBytes,
                  long creationDate,
                  @Nullable Map<String, String> extraDataMap,
                  @Nullable String signatureAsBase64,
                  String signerPubKeyAsHex,
                  List<String> bannedPrivilegedDevPubKeys,
                  boolean disableAutoConf,
                  List<String> bannedAutoConfExplorers,
                  Set<String> nodeAddressesBannedFromNetwork,
                  boolean disableMempoolValidation,
                  boolean disableApi) {
        this.bannedOfferIds = bannedOfferIds;
        this.nodeAddressesBannedFromTrading = nodeAddressesBannedFromTrading;
        this.bannedPaymentAccounts = bannedPaymentAccounts;
        this.bannedCurrencies = bannedCurrencies;
        this.bannedPaymentMethods = bannedPaymentMethods;
        this.arbitrators = arbitrators;
        this.seedNodes = seedNodes;
        this.priceRelayNodes = priceRelayNodes;
        this.preventPublicBtcNetwork = preventPublicBtcNetwork;
        this.btcNodes = btcNodes;
        this.disableTradeBelowVersion = disableTradeBelowVersion;
        this.mediators = mediators;
        this.refundAgents = refundAgents;
        this.bannedAccountWitnessSignerPubKeys = bannedAccountWitnessSignerPubKeys;
        this.btcFeeReceiverAddresses = btcFeeReceiverAddresses;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.creationDate = creationDate;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.signatureAsBase64 = signatureAsBase64;
        this.signerPubKeyAsHex = signerPubKeyAsHex;
        this.bannedPrivilegedDevPubKeys = bannedPrivilegedDevPubKeys;
        this.disableAutoConf = disableAutoConf;
        this.bannedAutoConfExplorers = bannedAutoConfExplorers;
        this.nodeAddressesBannedFromNetwork = nodeAddressesBannedFromNetwork;
        this.disableMempoolValidation = disableMempoolValidation;
        this.disableApi = disableApi;

        // ownerPubKeyBytes can be null when called from tests
        if (ownerPubKeyBytes != null) {
            ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);
        } else {
            ownerPubKey = null;
        }
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        List<protobuf.PaymentAccountFilter> paymentAccountFilterList = bannedPaymentAccounts.stream()
                .map(PaymentAccountFilter::toProtoMessage)
                .collect(Collectors.toList());

        protobuf.Filter.Builder builder = protobuf.Filter.newBuilder().addAllBannedOfferIds(bannedOfferIds)
                .addAllNodeAddressesBannedFromTrading(nodeAddressesBannedFromTrading)
                .addAllBannedPaymentAccounts(paymentAccountFilterList)
                .addAllBannedCurrencies(bannedCurrencies)
                .addAllBannedPaymentMethods(bannedPaymentMethods)
                .addAllArbitrators(arbitrators)
                .addAllSeedNodes(seedNodes)
                .addAllPriceRelayNodes(priceRelayNodes)
                .setPreventPublicBtcNetwork(preventPublicBtcNetwork)
                .addAllBtcNodes(btcNodes)
                .setDisableTradeBelowVersion(disableTradeBelowVersion)
                .addAllMediators(mediators)
                .addAllRefundAgents(refundAgents)
                .addAllBannedSignerPubKeys(bannedAccountWitnessSignerPubKeys)
                .addAllBtcFeeReceiverAddresses(btcFeeReceiverAddresses)
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSignerPubKeyAsHex(signerPubKeyAsHex)
                .setCreationDate(creationDate)
                .addAllBannedPrivilegedDevPubKeys(bannedPrivilegedDevPubKeys)
                .setDisableAutoConf(disableAutoConf)
                .addAllBannedAutoConfExplorers(bannedAutoConfExplorers)
                .addAllNodeAddressesBannedFromNetwork(nodeAddressesBannedFromNetwork)
                .setDisableMempoolValidation(disableMempoolValidation)
                .setDisableApi(disableApi);

        Optional.ofNullable(signatureAsBase64).ifPresent(builder::setSignatureAsBase64);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);

        return protobuf.StoragePayload.newBuilder().setFilter(builder).build();
    }

    public static Filter fromProto(protobuf.Filter proto) {
        List<PaymentAccountFilter> bannedPaymentAccountsList = proto.getBannedPaymentAccountsList().stream()
                .map(PaymentAccountFilter::fromProto)
                .collect(Collectors.toList());


        return new Filter(ProtoUtil.protocolStringListToList(proto.getBannedOfferIdsList()),
                ProtoUtil.protocolStringListToList(proto.getNodeAddressesBannedFromTradingList()),
                bannedPaymentAccountsList,
                ProtoUtil.protocolStringListToList(proto.getBannedCurrenciesList()),
                ProtoUtil.protocolStringListToList(proto.getBannedPaymentMethodsList()),
                ProtoUtil.protocolStringListToList(proto.getArbitratorsList()),
                ProtoUtil.protocolStringListToList(proto.getSeedNodesList()),
                ProtoUtil.protocolStringListToList(proto.getPriceRelayNodesList()),
                proto.getPreventPublicBtcNetwork(),
                ProtoUtil.protocolStringListToList(proto.getBtcNodesList()),
                proto.getDisableTradeBelowVersion(),
                ProtoUtil.protocolStringListToList(proto.getMediatorsList()),
                ProtoUtil.protocolStringListToList(proto.getRefundAgentsList()),
                ProtoUtil.protocolStringListToList(proto.getBannedSignerPubKeysList()),
                ProtoUtil.protocolStringListToList(proto.getBtcFeeReceiverAddressesList()),
                proto.getOwnerPubKeyBytes().toByteArray(),
                proto.getCreationDate(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap(),
                proto.getSignatureAsBase64(),
                proto.getSignerPubKeyAsHex(),
                ProtoUtil.protocolStringListToList(proto.getBannedPrivilegedDevPubKeysList()),
                proto.getDisableAutoConf(),
                ProtoUtil.protocolStringListToList(proto.getBannedAutoConfExplorersList()),
                ProtoUtil.protocolStringListToSet(proto.getNodeAddressesBannedFromNetworkList()),
                proto.getDisableMempoolValidation(),
                proto.getDisableApi()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public String toString() {
        return "Filter{" +
                "\n     bannedOfferIds=" + bannedOfferIds +
                ",\n     nodeAddressesBannedFromTrading=" + nodeAddressesBannedFromTrading +
                ",\n     bannedAutoConfExplorers=" + bannedAutoConfExplorers +
                ",\n     bannedPaymentAccounts=" + bannedPaymentAccounts +
                ",\n     bannedCurrencies=" + bannedCurrencies +
                ",\n     bannedPaymentMethods=" + bannedPaymentMethods +
                ",\n     arbitrators=" + arbitrators +
                ",\n     seedNodes=" + seedNodes +
                ",\n     priceRelayNodes=" + priceRelayNodes +
                ",\n     preventPublicBtcNetwork=" + preventPublicBtcNetwork +
                ",\n     btcNodes=" + btcNodes +
                ",\n     signatureAsBase64='" + signatureAsBase64 + '\'' +
                ",\n     signerPubKeyAsHex='" + signerPubKeyAsHex + '\'' +
                ",\n     ownerPubKeyBytes=" + Utilities.bytesAsHexString(ownerPubKeyBytes) +
                ",\n     disableTradeBelowVersion='" + disableTradeBelowVersion + '\'' +
                ",\n     mediators=" + mediators +
                ",\n     refundAgents=" + refundAgents +
                ",\n     bannedAccountWitnessSignerPubKeys=" + bannedAccountWitnessSignerPubKeys +
                ",\n     btcFeeReceiverAddresses=" + btcFeeReceiverAddresses +
                ",\n     creationDate=" + creationDate +
                ",\n     bannedPrivilegedDevPubKeys=" + bannedPrivilegedDevPubKeys +
                ",\n     extraDataMap=" + extraDataMap +
                ",\n     ownerPubKey=" + ownerPubKey +
                ",\n     disableAutoConf=" + disableAutoConf +
                ",\n     nodeAddressesBannedFromNetwork=" + nodeAddressesBannedFromNetwork +
                ",\n     disableMempoolValidation=" + disableMempoolValidation +
                ",\n     disableApi=" + disableApi +
                "\n}";
    }
}
