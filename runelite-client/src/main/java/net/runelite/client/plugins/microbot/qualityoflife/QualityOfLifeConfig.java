package net.runelite.client.plugins.microbot.qualityoflife;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("qualityOfLife")
public interface QualityOfLifeConfig extends Config {

	@ConfigSection(
		name = "Grand Exchange",
		description = "Grand Exchange QoL features",
		position = 0
	)
	String geSection = "geSection";

	@ConfigItem(
		keyName = "geAutoCopilot",
		name = "GE Auto Copilot",
		description = "When clicking Enter Quantity/Price in the GE, automatically presses 'e' to trigger Copilot suggestion then Enter to confirm.",
		position = 0,
		section = geSection
	)
	default boolean geAutoCopilot() {
		return true;
	}

	@ConfigItem(
		keyName = "geClerkHighlight",
		name = "Highlight GE Clerk on Complete",
		description = "Highlights Grand Exchange clerks when you have a fully bought or sold offer.",
		position = 1,
		section = geSection
	)
	default boolean geClerkHighlight() {
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "geClerkHighlightColor",
		name = "Clerk Highlight Color",
		description = "Color used to highlight GE clerks.",
		position = 2,
		section = geSection
	)
	default Color geClerkHighlightColor() {
		return new Color(0, 255, 0, 80);
	}

	@ConfigSection(
		name = "Banking",
		description = "Banking QoL features",
		position = 1
	)
	String bankingSection = "bankingSection";

	@ConfigItem(
		keyName = "bankEscOnWithdraw",
		name = "Close Bank on Withdraw",
		description = "Instantly presses Escape to close the bank after you withdraw an item.",
		position = 0,
		section = bankingSection
	)
	default boolean bankEscOnWithdraw() {
		return false;
	}

	@ConfigSection(
		name = "Skilling",
		description = "Skilling QoL features",
		position = 2
	)
	String skillingSection = "skillingSection";

	@ConfigItem(
		keyName = "autoSpaceProduction",
		name = "Auto Space Production Menu",
		description = "Spams spacebar when the Make/Fletching production menu appears to instantly confirm the last action.",
		position = 0,
		section = skillingSection
	)
	default boolean autoSpaceProduction() {
		return false;
	}
}
