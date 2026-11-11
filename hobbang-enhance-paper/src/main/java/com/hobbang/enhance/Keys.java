package com.hobbang.enhance;

import org.bukkit.NamespacedKey;

/** 플러그인 전역 키 (PDC, 레시피, 모디파이어 id). 네임스페이스는 전부 hobbang */
public final class Keys {
	private Keys() {}

	public static final String NS = "hobbang";

	public static NamespacedKey k(String path) {
		return new NamespacedKey(NS, path);
	}

	/** 아이템 PDC: 홉방 아이템 id (enhance_stone 등) */
	public static final NamespacedKey ID = k("id");
	/** 아이템 PDC: 강화 단계 */
	public static final NamespacedKey LEVEL = k("level");
	/** 아이템 PDC: 장인의 기운 (0~1000) */
	public static final NamespacedKey ARTISAN = k("artisan");
	/** 아이템 PDC: 강화 전 이름(이름표로 바꾼 이름) JSON */
	public static final NamespacedKey BASE_NAME = k("base_name");

	/** 청크 PDC: 강화대 위치 목록 (long[] packed) */
	public static final NamespacedKey FORGES = k("forges");

	/** 엔티티 PDC: 보스 종류 id */
	public static final NamespacedKey BOSS = k("boss");
	/** 엔티티 PDC: 보스 페이즈 */
	public static final NamespacedKey PHASE = k("phase");
	/** 엔티티 PDC: 보스 히트박스가 기억하는 비행 방향 등 */
	public static final NamespacedKey YAW = k("yaw");
	/** 엔티티 PDC: 보스가 부른 소환수 표시 */
	public static final NamespacedKey MINION = k("minion");

	/** 블록 엔티티 PDC (구조물 템플릿에 들어 있음): 보스 제단 = 보스 id (v1.4.0) */
	public static final NamespacedKey ALTAR = k("altar");
	/** 엔티티 PDC: 보스가 소환된 투기장 "site;world;x;y;z" (v1.4.0) */
	public static final NamespacedKey ARENA = k("arena");

	/** 플레이어 PDC: 첫 접속 가이드 지급 여부 */
	public static final NamespacedKey GOT_GUIDE = k("got_guide");
}
