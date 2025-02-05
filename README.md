# Group Bronzeman Mode - RuneLite Plugin
This plugin is a fork of [Another Bronzeman Mode](https://github.com/CodePanter/another-bronzeman-mode) that implements the custom gamemode called 'Bronzeman mode' but includes a group mode which will sync item unlocks between players.
The idea of this gamemode lies somewhere between a 'normal' account and an 'Ironman' account; as you can't buy an item on the Grand Exchange until you have obtained that item through other means, such as getting it as a drop or buying it in a shop.

The plugin enforces this rule by keeping track of all items you acquire, and only allowing you to buy this item.
When the plugin is enabled for the first time it will unlock all items in your inventory, as well as unlock all items in your bank the next time you open it.

## Required Setup for Group Mode
In order to properly sync items there is some setup that needs to be done.
- Make a copy of this [Google Sheet](https://docs.google.com/spreadsheets/d/1Lv042kwyM-ncEzY63ur2T2ePBvaju3Llpo4EgOGu4PQ/) to your drive.
- Get OAuth2 client credentials from Google. When sharing the file with **friends** skip to step 8) after one person follows steps 1-7.
    1. Go to https://console.cloud.google.com/home/
    2. Create a new project
    3. Navigate to APIs & Services > Library, and add the following:
        - Google Sheets API
        - Apps Script API
    4. Navigate to APIs & Services > Credentials
    5. Click `+ Create Credentials`. Then select create new OAuth client ID.
    6. Choose Application Type: Destktop App. Give it a name, then click create.
        - In the consent screen, add all of the users you want to allow in the group (this can be updated later).
    7. Close the prompt and then download the .json file for the OAuth2 client.
    8. Copy the contents of the file into the
        - NOTE: This file contains the client's secret and should NOT be posted publicly, as this is a security risk!
    9. Check mark the Google Sheet Authorize option.
    10. Send `!bmauth` in chat to log in to Google.
    11. Check mark the Enable group syncing option.

Each time you launch runelite, make sure to run the `!bmauth` command to re-log in to Google. Chat should remind you about this. The log in prompt may not display if the cached credentials are still valid.

## Features

- Restricts buying items from the Grand Exchange until that item is obtained through self-sufficient methods.
- Will disallow trading players depending on your settings.
- Shows an item unlock graphic every time you obtain an item for the first time.
- Unlocks are handled per account so you can have multiple bronze-men or not effect the status of your bronze-man accounts.
- Can optionally take screenshots of all new item unlocks.
- Supports adding a list of names of other Bronzeman accounts, and will provide them with (client side) Bronzeman chat icons.
- Has settings for sending chat messages and notifications for every item unlock.
- Allows the command '!bmcount' and '!bmunlocks' to get a total number for all unlocked items.
- Has a setting to enable a '!bmreset' command, which deletes all current unlocked items and starts fresh.
- Supports a '!bmbackup' command that makes a backup of the current unlocked items list.

## Screenshots

![Unlocking an item](https://i.imgur.com/odE4nVo.png)
Unlocking an item right after getting off tutorial island.

![Chat icons](https://i.imgur.com/D8Zl6Ss.png)
Talking to fellow Bronzemen with chat icons and everything.

![Grand exchange](https://i.imgur.com/lTd0I6P.png)
This player has only unlocked bronze arrows, so the other items are greyed out and not clickable.

![Collection log](https://i.imgur.com/6ae3Qml.png)
You can see all your unlocks in the collection log as a neatly ordered list of items.
Depending on your settings, you will either see this when you open the log, or under the 'Other' tab, scrolling all the way down and clicking "Bronzeman Unlocks".
This interface comes with search functionality, as well as the ability to re-lock an item by right clicking it and selecting "Remove".

## Credits

- First envisioned by [GUDI (Mod Ronan)](https://www.youtube.com/watch?v=GFNfa2saOJg)
- [Initial](https://github.com/sethrem/bronzeman) code written by [Sethrem](https://github.com/sethrem)
- Code improvements based on the [version](https://github.com/dekvall/bronzeman-mode) made by [Dekvall](https://github.com/dekvall)
- Unlock-list backup feature and reset unlocks feature written by [Robin Withes](https://github.com/robinwithes)
- Collection log integration and search functionality written by [Robin Withes](https://github.com/robinwithes)
Extended credits
- [Initial](https://github.com/CodePanter/another-bronzeman-mode) code written by [CodePanter](https://github.com/CodePanter)