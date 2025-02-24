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

package haveno.desktop.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Joiner;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import haveno.common.app.DevEnv;
import haveno.common.app.Log;
import haveno.common.config.Config;
import haveno.common.setup.GracefulShutDownHandler;
import haveno.common.setup.UncaughtExceptionHandler;
import haveno.common.util.Utilities;
import haveno.core.locale.Res;
import haveno.core.offer.OpenOffer;
import haveno.core.offer.OpenOfferManager;
import haveno.core.user.Cookie;
import haveno.core.user.CookieKey;
import haveno.core.user.Preferences;
import haveno.core.user.User;
import haveno.core.xmr.wallet.BtcWalletService;
import haveno.core.xmr.wallet.WalletsManager;
import haveno.desktop.common.view.CachingViewLoader;
import haveno.desktop.common.view.View;
import haveno.desktop.common.view.ViewLoader;
import haveno.desktop.main.MainView;
import haveno.desktop.main.debug.DebugView;
import haveno.desktop.main.overlays.popups.Popup;
import haveno.desktop.main.overlays.windows.BtcEmptyWalletWindow;
import haveno.desktop.main.overlays.windows.FilterWindow;
import haveno.desktop.main.overlays.windows.ManualPayoutTxWindow;
import haveno.desktop.main.overlays.windows.SendAlertMessageWindow;
import haveno.desktop.main.overlays.windows.ShowWalletDataWindow;
import haveno.desktop.util.CssTheme;
import haveno.desktop.util.ImageUtil;
import javafx.application.Application;
import javafx.geometry.BoundingBox;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static haveno.desktop.util.Layout.INITIAL_WINDOW_HEIGHT;
import static haveno.desktop.util.Layout.INITIAL_WINDOW_WIDTH;
import static haveno.desktop.util.Layout.MIN_WINDOW_HEIGHT;
import static haveno.desktop.util.Layout.MIN_WINDOW_WIDTH;

@Slf4j
public class HavenoApp extends Application implements UncaughtExceptionHandler {
    @Setter
    private static Consumer<Application> appLaunchedHandler;
    @Getter
    private static Runnable shutDownHandler;

    @Setter
    private Injector injector;
    @Setter
    private GracefulShutDownHandler gracefulShutDownHandler;
    private Stage stage;
    private boolean popupOpened;
    private Scene scene;
    private boolean shutDownRequested;
    private MainView mainView;

    public HavenoApp() {
        shutDownHandler = this::stop;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // JavaFx Application implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // NOTE: This method is not called on the JavaFX Application Thread.
    @Override
    public void init() {
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        appLaunchedHandler.accept(this);
    }

    public void startApplication(Runnable onApplicationStartedHandler) {
        log.info("Starting application");
        try {
            mainView = loadMainView(injector);
            mainView.setOnApplicationStartedHandler(onApplicationStartedHandler);
            scene = createAndConfigScene(mainView, injector);
            setupStage(scene);
        } catch (Throwable throwable) {
            log.error("Error during app init", throwable);
            handleUncaughtException(throwable, false);
        }
    }

    @Override
    public void stop() {
        if (!shutDownRequested) {
            new Popup().headLine(Res.get("popup.shutDownInProgress.headline"))
                    .backgroundInfo(Res.get("popup.shutDownInProgress.msg"))
                    .hideCloseButton()
                    .useAnimation(false)
                    .show();
            new Thread(() -> {
                gracefulShutDownHandler.gracefulShutDown(() -> {
                    log.info("App shutdown complete");
                });
            }).start();
            shutDownRequested = true;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UncaughtExceptionHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        if (!shutDownRequested) {
            if (scene == null) {
                log.warn("Scene not available yet, we create a new scene. The bug might be caused by an exception in a constructor or by a circular dependency in Guice. throwable=" + throwable.toString());
                scene = new Scene(new StackPane(), 1000, 650);
                CssTheme.loadSceneStyles(scene, CssTheme.CSS_THEME_LIGHT, false);
                stage.setScene(scene);
                stage.show();
            }
            try {
                try {
                    if (!popupOpened) {
                        popupOpened = true;
                        new Popup().error(Objects.requireNonNullElse(throwable.getMessage(), throwable.toString()))
                                .onClose(() -> popupOpened = false)
                                .show();
                    }
                } catch (Throwable throwable3) {
                    log.error("Error at displaying Throwable.");
                    throwable3.printStackTrace();
                }
                if (doShutDown)
                    stop();
            } catch (Throwable throwable2) {
                // If printStackTrace cause a further exception we don't pass the throwable to the Popup.
                log.error(throwable2.toString());
                if (doShutDown)
                    stop();
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Scene createAndConfigScene(MainView mainView, Injector injector) {
        //Rectangle maxWindowBounds = new Rectangle();
        Rectangle2D maxWindowBounds = new Rectangle2D(0, 0, 0, 0);
        try {
            maxWindowBounds = Screen.getPrimary().getBounds();
        } catch (IllegalArgumentException e) {
            // Multi-screen environments may encounter IllegalArgumentException (Window must not be zero)
            // Just ignore the exception and continue, which means the window will use the minimum window size below
            // since we are unable to determine if we can use a larger size
        }
        Scene scene = new Scene(mainView.getRoot(),
                maxWindowBounds.getWidth() < INITIAL_WINDOW_WIDTH ?
                        Math.max(maxWindowBounds.getWidth(), MIN_WINDOW_WIDTH) :
                        INITIAL_WINDOW_WIDTH,
                maxWindowBounds.getHeight() < INITIAL_WINDOW_HEIGHT ?
                        Math.max(maxWindowBounds.getHeight(), MIN_WINDOW_HEIGHT) :
                        INITIAL_WINDOW_HEIGHT);

        addSceneKeyEventHandler(scene, injector);

        Preferences preferences = injector.getInstance(Preferences.class);
        var config = injector.getInstance(Config.class);
        preferences.getCssThemeProperty().addListener((ov) -> {
            CssTheme.loadSceneStyles(scene, preferences.getCssTheme(), config.useDevModeHeader);
        });
        CssTheme.loadSceneStyles(scene, preferences.getCssTheme(), config.useDevModeHeader);

        return scene;
    }

    private void setupStage(Scene scene) {
        stage.setOnCloseRequest(event -> {
            event.consume();
            shutDownByUser();
        });

        // configure the primary stage
        String appName = injector.getInstance(Key.get(String.class, Names.named(Config.APP_NAME)));
        List<String> postFixes = new ArrayList<>();
        if (!Config.baseCurrencyNetwork().isMainnet()) {
            postFixes.add(Config.baseCurrencyNetwork().name());
        }
        if (injector.getInstance(Config.class).useLocalhostForP2P) {
            postFixes.add("LOCALHOST");
        }
        if (injector.getInstance(Config.class).useDevMode) {
            postFixes.add("DEV MODE");
        }
        if (!postFixes.isEmpty()) {
            appName += " [" + Joiner.on(", ").join(postFixes) + " ]";
        }

        stage.setTitle(appName);
        stage.setScene(scene);
        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);
        stage.getIcons().add(ImageUtil.getApplicationIconImage());

        User user = injector.getInstance(User.class);
        layoutStageFromPersistedData(stage, user);
        addStageLayoutListeners(stage, user);

        // make the UI visible
        stage.show();
    }

    private void layoutStageFromPersistedData(Stage stage, User user) {
        Cookie cookie = user.getCookie();
        cookie.getAsOptionalDouble(CookieKey.STAGE_X).flatMap(x ->
                cookie.getAsOptionalDouble(CookieKey.STAGE_Y).flatMap(y ->
                        cookie.getAsOptionalDouble(CookieKey.STAGE_W).flatMap(w ->
                                cookie.getAsOptionalDouble(CookieKey.STAGE_H).map(h -> new BoundingBox(x, y, w, h)))))
                .ifPresent(stageBoundingBox -> {
                    stage.setX(stageBoundingBox.getMinX());
                    stage.setY(stageBoundingBox.getMinY());
                    stage.setWidth(stageBoundingBox.getWidth());
                    stage.setHeight(stageBoundingBox.getHeight());
                });
    }

    private void addStageLayoutListeners(Stage stage, User user) {
        stage.widthProperty().addListener((observable, oldValue, newValue) -> {
            user.getCookie().putAsDouble(CookieKey.STAGE_W, (double) newValue);
            user.requestPersistence();
        });
        stage.heightProperty().addListener((observable, oldValue, newValue) -> {
            user.getCookie().putAsDouble(CookieKey.STAGE_H, (double) newValue);
            user.requestPersistence();
        });
        stage.xProperty().addListener((observable, oldValue, newValue) -> {
            user.getCookie().putAsDouble(CookieKey.STAGE_X, (double) newValue);
            user.requestPersistence();
        });
        stage.yProperty().addListener((observable, oldValue, newValue) -> {
            user.getCookie().putAsDouble(CookieKey.STAGE_Y, (double) newValue);
            user.requestPersistence();
        });
    }

    private MainView loadMainView(Injector injector) {
        CachingViewLoader viewLoader = injector.getInstance(CachingViewLoader.class);
        return (MainView) viewLoader.load(MainView.class);
    }

    private void addSceneKeyEventHandler(Scene scene, Injector injector) {
        scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
            if (Utilities.isCtrlPressed(KeyCode.W, keyEvent) ||
                    Utilities.isCtrlPressed(KeyCode.Q, keyEvent)) {
                shutDownByUser();
            } else {
                if (Utilities.isAltOrCtrlPressed(KeyCode.E, keyEvent)) {
                    injector.getInstance(BtcEmptyWalletWindow.class).show();
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.M, keyEvent)) {
                    injector.getInstance(SendAlertMessageWindow.class).show();
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.F, keyEvent)) {
                    injector.getInstance(FilterWindow.class).show();
                }
		else if (Utilities.isAltOrCtrlPressed(KeyCode.T, keyEvent)) {
                    // Toggle between show tor logs and only show warnings. Helpful in case of connection problems
                    String pattern = "org.berndpruenster.netlayer";
                    Level logLevel = ((Logger) LoggerFactory.getLogger(pattern)).getLevel();
                    if (logLevel != Level.DEBUG) {
                        log.info("Set log level for org.berndpruenster.netlayer classes to DEBUG");
                        Log.setCustomLogLevel(pattern, Level.DEBUG);
                    } else {
                        log.info("Set log level for org.berndpruenster.netlayer classes to WARN");
                        Log.setCustomLogLevel(pattern, Level.WARN);
                    }
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.J, keyEvent)) {
                    WalletsManager walletsManager = injector.getInstance(WalletsManager.class);
                    if (walletsManager.areWalletsAvailable())
                        new ShowWalletDataWindow(walletsManager).show();
                    else
                        new Popup().warning(Res.get("popup.warning.walletNotInitialized")).show();
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.G, keyEvent)) {
                    if (injector.getInstance(BtcWalletService.class).isWalletReady())
                        injector.getInstance(ManualPayoutTxWindow.class).show();
                    else
                        new Popup().warning(Res.get("popup.warning.walletNotInitialized")).show();
                } else if (DevEnv.isDevMode()) {
                    if (Utilities.isAltOrCtrlPressed(KeyCode.Z, keyEvent))
                        showDebugWindow(scene, injector);
                }
            }
        });
    }

    private void shutDownByUser() {
        boolean hasOpenOffers = false;
        for (OpenOffer openOffer : injector.getInstance(OpenOfferManager.class).getObservableList()) {
            if (openOffer.getState().equals(OpenOffer.State.AVAILABLE)) {
                hasOpenOffers = true;
                break;
            }
        }
        if (!hasOpenOffers) {
            // No open offers, so no need to show the popup.
            stop();
            return;
        }

        // We show a popup to inform user that open offers will be removed if Haveno is not running.
        String key = "showOpenOfferWarnPopupAtShutDown";
        if (injector.getInstance(Preferences.class).showAgain(key) && !DevEnv.isDevMode()) {
            new Popup().information(Res.get("popup.info.shutDownWithOpenOffers"))
                    .dontShowAgainId(key)
                    .useShutDownButton()
                    .closeButtonText(Res.get("shared.cancel"))
                    .show();
        } else {
            stop();
        }
    }

    // Used for debugging trade process
    private void showDebugWindow(Scene scene, Injector injector) {
        ViewLoader viewLoader = injector.getInstance(ViewLoader.class);
        View debugView = viewLoader.load(DebugView.class);
        Parent parent = (Parent) debugView.getRoot();
        Stage stage = new Stage();
        stage.setScene(new Scene(parent));
        stage.setTitle("Debug window"); // Don't translate, just for dev
        stage.initModality(Modality.NONE);
        stage.initStyle(StageStyle.UTILITY);
        stage.initOwner(scene.getWindow());
        stage.setX(this.stage.getX() + this.stage.getWidth() + 10);
        stage.setY(this.stage.getY());
        stage.show();
    }
}
