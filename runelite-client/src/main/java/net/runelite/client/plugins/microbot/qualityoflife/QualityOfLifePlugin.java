package net.runelite.client.plugins.microbot.qualityoflife;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Quality of Life",
	description = "Various quality of life improvements",
	tags = {"qol", "quality of life", "microbot", "grand exchange"},
	enabledByDefault = false
)
@Slf4j
public class QualityOfLifePlugin extends Plugin {

	private static final int CHATBOX_INPUT_WIDGET_ID = 10616870;
	private static final int GE_BACK_BUTTON_WIDGET_ID = 30474244;

	@Inject
	private QualityOfLifeConfig config;

	@Inject
	private QualityOfLifeOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	private boolean chatboxInputWasOpen = false;
	private boolean completedOffer = false;
	private boolean productionMenuWasOpen = false;

	@Override
	protected void startUp() {
		chatboxInputWasOpen = false;
		completedOffer = false;
		productionMenuWasOpen = false;
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() {
		chatboxInputWasOpen = false;
		completedOffer = false;
		productionMenuWasOpen = false;
		overlayManager.remove(overlay);
	}

	@com.google.inject.Provides
	QualityOfLifeConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(QualityOfLifeConfig.class);
	}

	boolean hasCompletedOffer() {
		return completedOffer;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!Microbot.isLoggedIn()) return;

		// GE Auto Copilot
		if (config.geAutoCopilot()) {
			boolean chatboxInputOpen = Rs2Widget.isWidgetVisible(CHATBOX_INPUT_WIDGET_ID);
			boolean onGeScreen = Rs2Widget.isWidgetVisible(GE_BACK_BUTTON_WIDGET_ID);
			if (chatboxInputOpen && !chatboxInputWasOpen && onGeScreen) {
				log.debug("GE chatbox input detected, triggering Copilot hotkey");
				triggerCopilotInput();
			}
			chatboxInputWasOpen = chatboxInputOpen;
		}

		// Auto Space on production menu
		if (config.autoSpaceProduction()) {
			boolean productionOpen = Rs2Widget.isProductionWidgetOpen();
			if (productionOpen && !productionMenuWasOpen) {
				log.debug("Production menu detected, spamming spacebar");
				spamSpacebar();
			}
			productionMenuWasOpen = productionOpen;
		}

		// GE Clerk highlight — check for completed offers
		if (config.geClerkHighlight()) {
			GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
			completedOffer = offers != null && Arrays.stream(offers).anyMatch(o ->
				o.getState() == GrandExchangeOfferState.BOUGHT
					|| o.getState() == GrandExchangeOfferState.SOLD);
		} else {
			completedOffer = false;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (!config.bankEscOnWithdraw()) return;
		if (!Rs2Bank.isOpen()) return;

		String option = event.getMenuOption();
		if (option != null && option.startsWith("Withdraw-")) {
			log.debug("Bank withdraw detected ({}), pressing Escape", option);
			CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(Rs2Random.between(50, 150));
					Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}
	}

	private void spamSpacebar() {
		CompletableFuture.runAsync(() -> {
			try {
				for (int i = 0; i < 5; i++) {
					Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
					Thread.sleep(Rs2Random.between(30, 80));
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	private void triggerCopilotInput() {
		CompletableFuture.runAsync(() -> {
			try {
				Rs2Keyboard.keyPress(69); // 'e' — KEY_PRESSED event to trigger Copilot hotkey

				int keyDelay = Rs2Random.randomGaussian(150.0, 40.0);
				keyDelay = Math.max(80, Math.min(300, keyDelay));
				Thread.sleep(keyDelay);

				Rs2Keyboard.keyPress(10); // Enter — confirm the value

				// Reset so a double-click (second open) is detected as a new transition
				chatboxInputWasOpen = false;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}
}
