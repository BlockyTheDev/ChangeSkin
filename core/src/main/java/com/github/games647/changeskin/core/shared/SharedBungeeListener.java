package com.github.games647.changeskin.core.shared;

import com.github.games647.changeskin.core.ChangeSkinCore;
import com.github.games647.changeskin.core.model.skin.SkinModel;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.util.UUID;

public abstract class SharedBungeeListener<P> {

    protected final ChangeSkinCore core;
    protected final String channelName;

    public SharedBungeeListener(ChangeSkinCore core) {
        this.core = core;
        this.channelName = core.getPlugin().getName();
    }

    protected void handlePayload(P player, byte[] data) {
        ByteArrayDataInput dataInput = ByteStreams.newDataInput(data);
        String subChannel = dataInput.readUTF();

        if ("UpdateSkin".equalsIgnoreCase(subChannel)) {
            core.getLogger().info("Received instant update request from BungeeCord. "
                    + "This request should only be send if the command /setskin was invoked");
            updateSkin(player, dataInput);
        } else if ("PermissionsCheck".equalsIgnoreCase(subChannel)) {
            checkPermissions(player, dataInput);
        }
    }


    private void updateSkin(P player, ByteArrayDataInput dataInput) throws IllegalArgumentException {
        String encodedData = dataInput.readUTF();
        if ("null".equalsIgnoreCase(encodedData)) {
            runUpdater(player, null);
            return;
        }

        String signature = dataInput.readUTF();
        String playerName = dataInput.readUTF();
        P receiver = getPlayerExact(playerName);
        core.getLogger().info("Instant update for {}", playerName);

        SkinModel skinData = SkinModel.createSkinFromEncoded(encodedData, signature);
        runUpdater(player, skinData);
    }

    private void checkPermissions(P player, ByteArrayDataInput dataInput) {
        int skinId = dataInput.readInt();
        String encodedData = dataInput.readUTF();
        String encodedSignature = dataInput.readUTF();

        //continue on success only
        String receiverUUID = dataInput.readUTF();
        boolean skinPerm = dataInput.readBoolean();
        boolean isOp = dataInput.readBoolean();

        SkinModel targetSkin = SkinModel.createSkinFromEncoded(encodedData, encodedSignature);
        if (isOp || checkBungeePerms(player, UUID.fromString(receiverUUID), targetSkin.getProfileId(), skinPerm)) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PermissionsSuccess");
            out.writeInt(skinId);
            out.writeUTF(encodedData);
            out.writeUTF(encodedSignature);
            out.writeUTF(receiverUUID);

            sendMessage(player, channelName, out.toByteArray());
        } else {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PermissionsFailure");
            sendMessage(player, channelName, out.toByteArray());
        }
    }

    private boolean checkBungeePerms(P player, UUID receiverUUID, UUID targetUUID, boolean skinPerm) {
        String pluginName = core.getPlugin().getName().toLowerCase();
        if (getUUID(player).equals(receiverUUID)) {
            boolean hasCommandPerm = hasPermission(player, pluginName + ".command.setskin");
            if (skinPerm) {
                return hasCommandPerm && checkWhitelistPermission(player, targetUUID);
            } else {
                return hasCommandPerm;
            }
        } else {
            boolean hasCommandPerm = hasPermission(player, pluginName + ".command.setskin.other");
            if (skinPerm) {
                return hasCommandPerm && checkWhitelistPermission(player, targetUUID);
            } else {
                return hasCommandPerm;
            }
        }
    }

    protected abstract void sendMessage(P player, String channel, byte[] data);

    protected abstract void runUpdater(P receiver, SkinModel targetSkin);

    protected abstract P getPlayerExact(String name);

    protected abstract UUID getUUID(P player);

    protected abstract boolean hasPermission(P player, String permission);

    protected abstract boolean checkWhitelistPermission(P player, UUID targetUUID);
}
