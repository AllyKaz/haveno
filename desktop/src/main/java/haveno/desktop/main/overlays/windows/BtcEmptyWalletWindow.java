package haveno.desktop.main.overlays.windows;

import com.google.inject.Inject;
import haveno.common.UserThread;
import haveno.core.api.CoreMoneroConnectionsService;
import haveno.core.locale.Res;
import haveno.core.offer.OpenOfferManager;
import haveno.core.util.FormattingUtils;
import haveno.core.util.coin.CoinFormatter;
import haveno.core.xmr.wallet.BtcWalletService;
import haveno.core.xmr.wallet.Restrictions;
import haveno.desktop.components.AutoTooltipButton;
import haveno.desktop.components.InputTextField;
import haveno.desktop.main.overlays.Overlay;
import haveno.desktop.main.overlays.popups.Popup;
import haveno.desktop.util.GUIUtil;
import haveno.desktop.util.Transitions;
import haveno.network.p2p.P2PService;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.concurrent.TimeUnit;

import static haveno.desktop.util.FormBuilder.addInputTextField;
import static haveno.desktop.util.FormBuilder.addMultilineLabel;
import static haveno.desktop.util.FormBuilder.addTopLabelTextField;

public final class BtcEmptyWalletWindow extends Overlay<BtcEmptyWalletWindow> {
    protected static final Logger log = LoggerFactory.getLogger(BtcEmptyWalletWindow.class);

    private final WalletPasswordWindow walletPasswordWindow;
    private final OpenOfferManager openOfferManager;
    private final P2PService p2PService;
    private final CoreMoneroConnectionsService connectionService;
    private final BtcWalletService btcWalletService;
    private final CoinFormatter btcFormatter;

    private Button emptyWalletButton;
    private InputTextField addressInputTextField;
    private TextField balanceTextField;

    @Inject
    public BtcEmptyWalletWindow(WalletPasswordWindow walletPasswordWindow,
                                OpenOfferManager openOfferManager,
                                P2PService p2PService,
                                CoreMoneroConnectionsService connectionService,
                                BtcWalletService btcWalletService,
                                @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        headLine(Res.get("emptyWalletWindow.headline", "BTC"));
        width = 768;
        type = Type.Instruction;

        this.p2PService = p2PService;
        this.connectionService = connectionService;
        this.btcWalletService = btcWalletService;
        this.btcFormatter = btcFormatter;
        this.walletPasswordWindow = walletPasswordWindow;
        this.openOfferManager = openOfferManager;
    }

    @Override
    public void show() {
        createGridPane();
        addHeadLine();
        addContent();
        applyStyles();
        display();
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    private void addContent() {
        addMultilineLabel(gridPane, ++rowIndex, Res.get("emptyWalletWindow.info"), 0);

        Coin totalBalance = btcWalletService.getAvailableConfirmedBalance();
        balanceTextField = addTopLabelTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.balance"),
                btcFormatter.formatCoinWithCode(totalBalance), 10).second;

        addressInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.address"));

        closeButton = new AutoTooltipButton(Res.get("shared.cancel"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        emptyWalletButton = new AutoTooltipButton(Res.get("emptyWalletWindow.button"));
        boolean isBalanceSufficient = Restrictions.isAboveDust(totalBalance);
        emptyWalletButton.setDefaultButton(isBalanceSufficient);
        emptyWalletButton.setDisable(!isBalanceSufficient && addressInputTextField.getText().length() > 0);
        emptyWalletButton.setOnAction(e -> {
            if (addressInputTextField.getText().length() > 0 && isBalanceSufficient) {
                log.warn(getClass().getSimpleName() + ".addContent() needs updated for XMR");
            }
        });

        closeButton.setDefaultButton(!isBalanceSufficient);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        hBox.getChildren().addAll(emptyWalletButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }

    private void doEmptyWallet(KeyParameter aesKey) {
        if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, connectionService)) {
            if (!openOfferManager.getObservableList().isEmpty()) {
                UserThread.runAfter(() ->
                        new Popup().warning(Res.get("emptyWalletWindow.openOffers.warn"))
                                .actionButtonText(Res.get("emptyWalletWindow.openOffers.yes"))
                                .onAction(() -> doEmptyWallet2(aesKey))
                                .show(), 300, TimeUnit.MILLISECONDS);
            } else {
                doEmptyWallet2(aesKey);
            }
        }
    }

    private void doEmptyWallet2(KeyParameter aesKey) {
        emptyWalletButton.setDisable(true);
        openOfferManager.removeAllOpenOffers(() -> {
            try {
                btcWalletService.emptyBtcWallet(addressInputTextField.getText(),
                        aesKey,
                        () -> {
                            closeButton.updateText(Res.get("shared.close"));
                            balanceTextField.setText(btcFormatter.formatCoinWithCode(btcWalletService.getAvailableConfirmedBalance()));
                            emptyWalletButton.setDisable(true);
                            log.debug("wallet empty successful");
                            onClose(() -> UserThread.runAfter(() -> new Popup()
                                    .feedback(Res.get("emptyWalletWindow.sent.success"))
                                    .show(), Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS));
                            doClose();
                        },
                        (errorMessage) -> {
                            emptyWalletButton.setDisable(false);
                            log.error("wallet empty failed {}", errorMessage);
                        });
            } catch (InsufficientMoneyException | AddressFormatException e1) {
                e1.printStackTrace();
                log.error(e1.getMessage());
                emptyWalletButton.setDisable(false);
            }
        });
    }
}
