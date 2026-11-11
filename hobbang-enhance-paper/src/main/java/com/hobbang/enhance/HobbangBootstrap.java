package com.hobbang.enhance;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.net.URI;
import java.net.URL;

/** 서버 시작 시 내장 데이터팩(인첸트 20종 + 발전과제)을 자동 등록 → 별도 데이터팩 설치 불필요 */
public final class HobbangBootstrap implements PluginBootstrap {
	@Override
	public void bootstrap(BootstrapContext context) {
		context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
			try {
				URL url = HobbangBootstrap.class.getResource("/hobbang_datapack");
				if (url == null) {
					context.getLogger().error("내장 데이터팩을 찾을 수 없습니다 (/hobbang_datapack)");
					return;
				}
				URI uri = url.toURI();
				event.registrar().discoverPack(uri, "provided");
			} catch (Exception e) {
				context.getLogger().error("내장 데이터팩 등록 실패", e);
			}
		}));
	}
}
