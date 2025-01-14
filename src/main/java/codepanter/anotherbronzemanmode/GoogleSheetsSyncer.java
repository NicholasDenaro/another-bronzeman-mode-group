/*
 * Copyright (c) 2021, Nicholas Denaro <ndenarodev@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package codepanter.anotherbronzemanmode;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import javax.inject.Inject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static net.runelite.http.api.RuneLiteAPI.GSON;

public class GoogleSheetsSyncer
{
    @Inject
    private Client client;

    @Inject
    AnotherBronzemanModeConfig config;

    private Credential storedCredential;

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ChatMessageManager chatMessageManager;

    private AnotherBronzemanModePlugin plugin;

    public void authorize(AnotherBronzemanModePlugin plugin)
    {
        executor.execute(this::authorizactionCodeFlow);
        this.plugin = plugin;
    }

    private void authorizactionCodeFlow()
    {
        if (storedCredential != null)
        {
            if (refreshToken())
            {
                return;
            }
        }

        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            // Load client secrets.
            InputStream in = new ByteArrayInputStream(plugin.getOAuth2ClientDetails().getBytes(StandardCharsets.UTF_8));

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new File(Paths.get(plugin.playerFolder.getAbsolutePath(), TOKENS_DIRECTORY_PATH).toAbsolutePath().toString()));
            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(dataStoreFactory)
                    .setApprovalPrompt("force")
                    .setAccessType("offline")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            storedCredential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(client.getUsername());
            sendChatMessage("Authorization completed successfully.");

            if (storedCredential.getRefreshToken() == null)
            {
                sendChatMessage("No refresh token.");
            }

            if (storedCredential.getExpiresInSeconds() < 0)
            {
                sendChatMessage("token already expired, refreshing...");
                if (!storedCredential.refreshToken())
                {
                    sendChatMessage("Authorization failed for expired token. Try deleting token file or running !bmauth again.");
                    return;
                }

                sendChatMessage("Refresh successful.");
            }
        }
        catch (Exception ex)
        {
            sendChatMessage("Authorization failed.");
        }
    }

    public boolean refreshToken()
    {
        try
        {
            long expiresInSeconds = storedCredential.getExpiresInSeconds();
            if (storedCredential.getAccessToken() == null)
            {
                sendChatMessage("No Google access token, run !bmauth to continue syncing data.");
            }
            else if (storedCredential.refreshToken())
            {
                if (storedCredential.getExpiresInSeconds() >= expiresInSeconds) {
                    sendChatMessage("Refresh Google client token completed successfully.");
                    storedCredential.setAccessToken(storedCredential.getAccessToken());
                    return true;
                }
                else
                {
                    storedCredential = null;
                    sendChatMessage("Token refresh failed, run !bmauth to continue syncing data.");
                }
            }
            else
            {
                storedCredential = null;
                sendChatMessage("Google token expired, run !bmauth to continue syncing data.");
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            storedCredential = null;
            sendChatMessage("Exception occurred while refreshing token. run !bmauth to continue syncing data.");
        }

        return false;
    }

    private void sendChatMessage(String chatMessage)
    {
        final String message = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append(chatMessage)
                .build();

        chatMessageManager.queue(
                QueuedMessage.builder()
                        .type(ChatMessageType.CONSOLE)
                        .runeLiteFormattedMessage(message)
                        .build());
    }

    public void savePlayerUnlocks(List<Integer> unlockedItems)
    {
        if (storedCredential == null)
        {
            sendChatMessage("Use !bmauth to allow for syncing unlocks.");
            return;
        }

        try {
            SheetsBatchGet response = SheetsRequest(String.format("https://sheets.googleapis.com/v4/spreadsheets/%s/values:batchGet?majorDimension=ROWS&ranges=1:1&access_token=%s", config.syncSheetId(), storedCredential.getAccessToken()));

            if (response == null)
            {
                refreshToken();
                response = SheetsRequest(String.format("https://sheets.googleapis.com/v4/spreadsheets/%s/values:batchGet?majorDimension=ROWS&ranges=1:1&access_token=%s", config.syncSheetId(), storedCredential.getAccessToken()));
                if (response == null)
                {
                    return;
                }
            }

            int foundColumn = -1;
            int column = -1;
            String columnName = "";
            if (response.valueRanges[0].values != null) {
                List<String> users = Arrays.asList(response.valueRanges[0].values[0]);
                column = users.indexOf(client.getUsername());
                foundColumn = column;
                do {
                    int colPart = column % 26;
                    columnName = (char) ('a' + colPart) + columnName;
                    column = (column / 26);
                } while (column-- > 0);
            }

            if (foundColumn == -1) {
                // Set row number for new player
                response = SheetsRequest(String.format("https://sheets.googleapis.com/v4/spreadsheets/%s/values:batchGet?majorDimension=ROWS&ranges=NewSlot&access_token=%s", config.syncSheetId(), storedCredential.getAccessToken()));
                columnName = response.valueRanges[0].values[0][0];
            }

            SheetsPutRequest(
                    String.format(
                            "https://sheets.googleapis.com/v4/spreadsheets/%s/values/CharacterData!%s:%s?valueInputOption=RAW&access_token=%s",
                            config.syncSheetId(),
                            columnName,
                            columnName,
                            storedCredential.getAccessToken()),
                    new PlayerDataForSheets(unlockedItems)
            );
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            sendChatMessage("Error syncing saved unlocks. Try running !bmauth again.");
        }
    }

    public Set<Integer> loadPlayerUnlocks(Set<Integer> previousItems)
    {
        if (storedCredential == null)
        {
            sendChatMessage("Use !bmauth to allow for syncing unlocks.");
            return null;
        }

        SheetsBatchGet response = null;

        try {
            response = SheetsRequest(String.format("https://sheets.googleapis.com/v4/spreadsheets/%s/values:batchGet?majorDimension=COLUMNS&ranges=Rollups!c:c&access_token=%s", config.syncSheetId(), storedCredential.getAccessToken()));
        }
        catch (Exception ex)
        {
            response = null;
        }

        if (response == null)
        {
            refreshToken();
            response = SheetsRequest(String.format("https://sheets.googleapis.com/v4/spreadsheets/%s/values:batchGet?majorDimension=COLUMNS&ranges=Rollups!c:c&access_token=%s", config.syncSheetId(), storedCredential.getAccessToken()));
            if (response == null) {
                return null;
            }
        }

        if (response.valueRanges[0].values != null)
        {
            List<String> items = Arrays.asList(response.valueRanges[0].values[0]);
            Set<Integer> newItems = new HashSet<Integer>(items.stream().map(Integer::parseInt).collect(Collectors.toList()));
            newItems.removeAll(previousItems);
            return newItems;
        }

        return new HashSet<Integer>();
    }

    class SheetsBatchGet
    {
        public String spreadsheetId;
        public ValueRanges[] valueRanges;
    }

    class ValueRanges
    {
        public String range;
        public String majorDimension;
        public String[][] values;
    }

    class PlayerDataForSheets
    {
        public String[][] values;

        public PlayerDataForSheets(List<Integer> data)
        {
            values = new String[data.size() + 1][];
            values[0] = new String[1];
            values[0][0] = client.getUsername();
            for (int c = 0; c < data.size(); c++)
            {
                values[c + 1] = new String[1];
                values[c + 1][0] = "" + data.get(c);
            }
        }
    }

    private SheetsBatchGet SheetsRequest(String resource)
    {
        try {
            URL url = new URL(resource);
            HttpURLConnection connection = null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String payload = "";
            String line;
            while ((line = rd.readLine()) != null) {
                payload += line + "\n";
            }
            rd.close();
            return GSON.fromJson(payload, SheetsBatchGet.class);
        }
        catch(Exception ex)
        {
            sendChatMessage("Failed to get synced unlocked items.");
            System.out.println(ex.getMessage());
            return null;
        }
    }

    private void SheetsPutRequest(String resource, PlayerDataForSheets data)
    {
        try {
            URL url = new URL(resource);
            HttpURLConnection connection = null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            BufferedWriter wd = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            String jsonBody = GSON.toJson(data);
            wd.write(jsonBody);
            wd.flush();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String payload = "";
            String line;
            while ((line = rd.readLine()) != null) {
                payload += line + "\n";
            }
            rd.close();
            wd.close();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            sendChatMessage("Failed to update synced unlocked items.");
        }
    }
}
