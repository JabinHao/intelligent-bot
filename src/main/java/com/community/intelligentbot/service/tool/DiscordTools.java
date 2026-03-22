package com.community.intelligentbot.service.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class DiscordTools {

    private final JDA jda;

    public DiscordTools(JDA jda) {
        this.jda = jda;
    }

    @Tool("Get the current user's Discord profile: username, display name, account creation date. Use when user asks about themselves, e.g. 'who am I' / '我是谁' / '私は誰' / '나는 누구'")
    public String getUserInfo(@ToolMemoryId String memoryId) {
        String userId = parseUserId(memoryId);

        User user = jda.retrieveUserById(userId).complete();
        if (user == null) {
            return "User not found.";
        }

        return String.format("Username: %s, Display name: %s, Account created: %s, Avatar: %s",
                user.getName(),
                user.getGlobalName() != null ? user.getGlobalName() : user.getName(),
                user.getTimeCreated().toLocalDate(),
                user.getEffectiveAvatarUrl());
    }

    @Tool("Get the current user's guild membership: nickname, roles, join date. Use when user asks about their roles or membership, e.g. 'what are my roles' / '我的角色' / '내 역할'")
    public String getGuildMemberInfo(@ToolMemoryId String memoryId) {
        String userId = parseUserId(memoryId);
        String guildId = parseGuildId(memoryId);
        if (guildId == null) {
            return "Not in a guild context, unable to retrieve member info.";
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return "Guild not found.";
        }

        Member member = guild.retrieveMemberById(userId).complete();
        if (member == null) {
            return "Member not found in this guild.";
        }

        String roles = member.getRoles().stream()
                .map(role -> role.getName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("None");

        return String.format("Nickname: %s, Roles: [%s], Joined guild: %s",
                member.getNickname() != null ? member.getNickname() : "None",
                roles,
                member.getTimeJoined().toLocalDate());
    }

    @Tool("Get current Discord server info: name, member count, description. Use when user asks about this server/guild, e.g. 'what server is this' / '这是什么服务器' / '이 서버는 뭐야'")
    public String getGuildInfo(@ToolMemoryId String memoryId) {
        String guildId = parseGuildId(memoryId);
        if (guildId == null) {
            return "Not in a guild context, unable to retrieve guild info.";
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return "Guild not found.";
        }

        return String.format("Guild name: %s, Member count: %d, Description: %s",
                guild.getName(),
                guild.getMemberCount(),
                guild.getDescription() != null ? guild.getDescription() : "None");
    }

    // memoryId format: botId:userId:dm or botId:userId:guild:guildId
    private String parseUserId(String memoryId) {
        return memoryId.split(":")[1];
    }

    private String parseGuildId(String memoryId) {
        String[] parts = memoryId.split(":");
        if (parts.length >= 4 && "guild".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
}
