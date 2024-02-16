package scripts;
import org.tribot.script.sdk.*;
import org.tribot.script.sdk.input.Keyboard;
import org.tribot.script.sdk.painting.Painting;
import org.tribot.script.sdk.painting.template.basic.*;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.script.TribotScript;
import org.tribot.script.sdk.script.TribotScriptManifest;
import org.tribot.script.sdk.types.Area;
import org.tribot.script.sdk.types.InventoryItem;
import org.tribot.script.sdk.types.WorldTile;
import org.tribot.script.sdk.Bank;
import org.tribot.script.sdk.walking.LocalWalking;

import java.awt.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.Random;


@TribotScriptManifest(name = "[Iron]MotherlodeMine", author = "Amorphous", category = "qDev", description = "MotherLode mining script")
public class MyScript implements TribotScript {

	@Override
	@SuppressWarnings("InfiniteLoopStatement")
	public void execute(final String args) {

		// before anything else, import classes!
		Travel travel = new Travel();
		Information info = new Information();
		MiningActions mactions = new MiningActions();
		StrutHandler strutHandler = new StrutHandler();

		double scriptStartTime = System.currentTimeMillis();
		double worldStartTime = System.currentTimeMillis();



		int oreSackMaximum = 80;
		String[] userArgs;
		boolean userHasUnlockedLargerOreSack = true;
		boolean userHasUnlockedUpperMine = true;

		Waiting.wait(1000);
		Log.trace("Login bot initiated.");
		Login.login();

		BooleanSupplier isLoggedIn = Login::isLoggedIn;
		Waiting.waitUntil(isLoggedIn);

		Log.info("Login successful. Welcome human.");

		double startingMiningXP = Skill.MINING.getXp();
		Log.trace("Setting the starting mining XP to " + startingMiningXP);


		PaintTextRow template = PaintTextRow.builder().background(Color.green.darker()).build();
		// template is optional stuff to not repeat same colors/fonts for every row

		BasicPaintTemplate paint = BasicPaintTemplate.builder()
				.row(PaintRows.scriptName(template.toBuilder()))
				.row(PaintRows.runtime(template.toBuilder()))
				//.row(template.toBuilder().label("Test").value("ing").onClick(() -> Log.log("CLICKED!")).build())
				.row(template.toBuilder().label("XP/hr").value(() -> {
					double bignum = (3600000);
					double divisor = (System.currentTimeMillis() - scriptStartTime) / bignum;
					divisor = divisor * 1000;
					String output = String.valueOf(Math.round((Skill.MINING.getXp() - startingMiningXP) / divisor));
					return output + "k";
				}).build())
				.row(template.toBuilder().label("TTL:").value(() -> {
					double bignum = (3600000);
					double divisor = (System.currentTimeMillis() - scriptStartTime) / bignum;
					divisor = divisor * 1000;
					double hourlyXP = Math.round((Skill.MINING.getXp() - startingMiningXP) / divisor);
					double nextLevel = Skill.MINING.getCurrentXpToNextLevel();
					double timeToNextLevel = (nextLevel / hourlyXP) / 1000;
					return String.format("%.2f",timeToNextLevel).concat(" hrs");
				}).build())
				.location(PaintLocation.BOTTOM_LEFT_VIEWPORT)
				.build();
		Painting.addPaint(paint::render);
		Log.debug("Paint initialized");

		// theres an issue with the input and whitespace
		// if the input is such that --> "false, true", that space between fucks it up.

		if(!args.isEmpty()){
			Log.info(args);
			String[] myArgs = retrieveArgs(args);
			Log.debug("myARgs = " + myArgs[0] + myArgs[1]);
			userHasUnlockedLargerOreSack = Boolean.parseBoolean(myArgs[0]);			// the first argument will be the oreSackCount
			Log.debug(userHasUnlockedLargerOreSack);
			userHasUnlockedUpperMine = Boolean.parseBoolean(myArgs[1]);			// second argument is a boolean, to check if the upper mine is unlocked
			Log.debug(userHasUnlockedUpperMine);
		}


		if (userHasUnlockedLargerOreSack) {
			oreSackMaximum = 160;
			Log.warn("oreSackMaximim is now set to " + oreSackMaximum);
		}

		int hopperCount;
		int inventoryCount;
		int plannedDurationForCurrentWorld = new Random().nextInt(120 + 1) + 60;
		Log.info("We are planning to stay in this world for " + plannedDurationForCurrentWorld + " minutes.");

		Camera.setZoomPercent(0);

		Optional<InventoryItem> payDirtExistsInSatchel = itemExistsInSatchel("Pay-dirt");
		Optional<InventoryItem> oreExistsInSatchel = itemExistsInSatchel("ore");
		Optional<InventoryItem> coalExistsInSatchel = itemExistsInSatchel("Coal");
		Optional<InventoryItem> nuggetExistsInSatchel = itemExistsInSatchel("Golden nugget");
		Optional<InventoryItem> gemsExistInSatchel = itemExistsInSatchel("Uncut");
		Optional<InventoryItem> theHammerHolster = hasHammer();


		if(theHammerHolster.isEmpty()) {
			Log.trace("Walking to the crate to get a hammer");
			travel.walkToHammerCrate();
			grabHammerFromCrate();

			while(theHammerHolster.isEmpty()) {
				theHammerHolster = hasHammer();
				Waiting.waitNormal(1000,635);
			}
			Log.info("We have obtained a hammer");
		}

		if(payDirtExistsInSatchel.isPresent()){
			Log.warn("Pay dirt from a previous run exists. Let's deposit it in the hopper.");
			travel.checkIfWeShouldRun();
			travel.getToTheHopper();
			washOresInHopper();
			Log.trace("Pay dirt deposited");
			strutInquiry(travel);
			travel.checkIfWeShouldRun();
		}

		if(
			oreExistsInSatchel.isPresent() ||
			coalExistsInSatchel.isPresent() ||
			nuggetExistsInSatchel.isPresent() ||
			gemsExistInSatchel.isPresent())
		{

			Log.warn("Ore / Gems / Nuggets detected in inventory. Walking to bank.");
			travel.walkToDepositBin();
			BooleanSupplier bankIsOpen = this::openDepositBin;
			Waiting.waitUntil(bankIsOpen);

			while (!Bank.isDepositBoxOpen()){
				Waiting.waitNormal(100, 5); // let's sleep before the next action
				Log.debug("Bank is not open");
			}

			depositOresAndGems();
			Waiting.waitNormal(1000,67);
		}


		while(true){
			Log.warn("Mining behavior selected");
			long timeNOW = System.currentTimeMillis();
			int currentDurationInMinutes = (int)(timeNOW - scriptStartTime)/(1000*60);
			int currentDurationInThisWorld = (int)(timeNOW - worldStartTime)/(1000*60);
			int gainedMiningXP = (int)(Skill.MINING.getXp() - startingMiningXP);
			int xpPerHour;

			if(currentDurationInMinutes > 0) {
				gainedMiningXP = (int)(Skill.MINING.getXp() - startingMiningXP);
				xpPerHour = (int)((gainedMiningXP / currentDurationInMinutes) * 60);
				getSessionStats(currentDurationInMinutes, gainedMiningXP, xpPerHour);

			}

			if(currentDurationInThisWorld > plannedDurationForCurrentWorld) {
				Log.warn("Oh shit it's been a while, lets world hop!");

				int currentWorld = WorldHopper.getCurrentWorld();
				int[] usWorlds = {305, 314, 321, 322, 329, 330, 337, 346, 354, 362, 369, 370, 377, 386, 477, 478, 479, 480, 481, 482, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 494, 495, 496};

				ArrayList<Integer> list = new ArrayList<>(Arrays.asList(Arrays
						.stream(usWorlds)
						.boxed()
						.toArray(Integer[]::new)
				));

				list.remove(Integer.valueOf(currentWorld));
				usWorlds = list.stream().mapToInt(Integer::intValue).toArray();

				int nextWorld = new Random().nextInt(usWorlds.length);
				System.out.print("random index = " + nextWorld);
				nextWorld = usWorlds[nextWorld];

				Log.warn("Next world selected --> " + nextWorld);
				WorldHopper.hop(nextWorld);

				BooleanSupplier hasReturnedFromHop = Login::isLoggedIn;

				Waiting.waitUntil(hasReturnedFromHop);

				currentWorld = WorldHopper.getCurrentWorld();

				if(currentWorld == nextWorld){
					Log.info("we made it to world " + nextWorld + "!");
					worldStartTime = System.currentTimeMillis();
					plannedDurationForCurrentWorld = new Random().nextInt(120 + 1) + 60;
					Log.info("We are planning to stay in this world for " + plannedDurationForCurrentWorld + " minutes.");
				}
			}
			else {
				Log.info("We have been on world  " + WorldHopper.getCurrentWorld() + " for " + currentDurationInThisWorld + " minutes. We will hop in " + (plannedDurationForCurrentWorld - currentDurationInThisWorld) + " minutes.");
			}

			hopperCount = GameState.getVarbit(5558);
			inventoryCount = Query.inventory().count();

			int payDirtCount = (Query.inventory().nameContains("Pay-dirt").sumStacks());
			int calculatedHopperCount = hopperCount + payDirtCount;

			if (calculatedHopperCount > oreSackMaximum){

				Log.debug("The ore sack is very full. Let's go deal with that first");
				travel.getToTheHopper();
				Log.debug("But lets wash these first.");
				washOresInHopper();

				processOresInSack(travel);
				Log.info("Ore sack has been cleaned out.");
			}
			else if (inventoryCount != 28) {

				Log.debug(args);

				Log.info("userHasUnlockedUpperMine = " + userHasUnlockedUpperMine);

				Area selectedMine;
				Area MOTHERLODE_SOUTH = Area.fromRadius(new WorldTile(3737, 5649, -1), 1);
				Area UPPER_CLOSE = Area.fromRadius(new WorldTile(3760, 5672, -1), 1);
				Area lowerLadder = Area.fromRadius(new WorldTile(3755, 5672, -1), 1);
				Area upperLadder = Area.fromRadius(new WorldTile(3755, 5675, -1), 2);

				if(userHasUnlockedUpperMine) {
					selectedMine = upperLadder;
					while (!selectedMine.containsMyPlayer()) {

						Log.trace("Walking to the mine!");
						Log.debug("walking to lower ladder");
						if (LocalWalking.walkTo(lowerLadder.getCenter()) && Waiting.waitUntil(lowerLadder::containsMyPlayer)) {
							Log.debug("arrived at lower ladder");
							Waiting.waitNormal(600, 90);
							BooleanSupplier hasClimbedLadder = this::clickLadder;
							Waiting.waitUntil(hasClimbedLadder);
							Waiting.waitUntil(selectedMine::containsMyPlayer);
							Waiting.waitNormal(1300,425);
						}
					}
				}
				else {
					selectedMine = MOTHERLODE_SOUTH;
					Log.warn("lower mine selected, south");
					Log.trace("Walking to the mine!");
					if (LocalWalking.walkTo(selectedMine.getCenter())) {
						Waiting.waitUntil(selectedMine::containsMyPlayer);
						Waiting.waitNormal(1300,425);
					}
				}

				inventoryCount = Query.inventory().count();
				boolean isMining = false;
				long miningStartTime = 0, miningCurrentTime = 0;

				while(Query.inventory().count() < 28) {
					int currentHopperCount = GameState.getVarbit(5558);

					mine("Ore vein");
					Log.trace("You swing your pick at the rock...");
					Waiting.waitNormal(1000,5);
					isMining = true;

					while(isMining){
						isMining = MyPlayer.isAnimating();
						Waiting.wait(600);
						if(!isMining) {
							Waiting.waitNormal(600, 25);
							isMining = MyPlayer.isAnimating();
							if(!isMining) {
								Waiting.waitNormal(600, 25);
								isMining = MyPlayer.isAnimating();
								if(!isMining) {
									Waiting.waitNormal(600, 25);
									isMining = MyPlayer.isAnimating();
									if(!isMining) {
										Waiting.waitNormal(600, 25);
									}
								}
							}
						}
					}
					inventoryCount = Query.inventory().count();
				}
			}

			else {
				Log.warn("Inventory is full");
				inventoryCount = Query.inventory().count();
			}

			boolean condition1 = hopperCount <= oreSackMaximum;
			boolean condition2 = payDirtCount > 0;

			boolean shouldDeposit = (condition1 && condition2) ;
			Log.debug("shouldDeposit =  " + shouldDeposit);

			// the second part of this is basically checking if something depositable exists, minus the pickaxe and hammer
			if(shouldDeposit) {
				Waiting.waitNormal(1300, 17);
				travel.checkIfWeShouldRun();
				travel.getToTheHopper();

				BooleanSupplier hasDepositedPayDirt = this::washOresInHopper;
				Waiting.waitUntil(hasDepositedPayDirt);
				Log.trace("Pay dirt deposited");
				strutInquiry(travel);
				travel.checkIfWeShouldRun();
			}


		}
	}















}
