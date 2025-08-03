package it.mattiolservices.coralclans.clan.enums;

public enum ClanRole {
    LEADER, OFFICER, MEMBER;

    public boolean canManage(ClanRole other) {
        return this.ordinal() <= other.ordinal();
    }
}