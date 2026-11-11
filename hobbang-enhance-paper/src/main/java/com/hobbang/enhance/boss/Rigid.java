package com.hobbang.enhance.boss;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 강체 변환 (위치 + 회전). 보스 모델 계층 구조 계산용 (Fabric 판과 동일) */
public record Rigid(Vector3f pos, Quaternionf rot) {
	public static Rigid yaw(float yawDeg) {
		return new Rigid(new Vector3f(), new Quaternionf().rotationY((float) Math.toRadians(-yawDeg)));
	}

	public Rigid at(float x, float y, float z) {
		Vector3f p = new Vector3f(x, y, z).rotate(rot).add(pos);
		return new Rigid(p, new Quaternionf(rot));
	}

	public Rigid rotX(float deg) {
		return new Rigid(new Vector3f(pos), new Quaternionf(rot).rotateX((float) Math.toRadians(deg)));
	}

	public Rigid rotY(float deg) {
		return new Rigid(new Vector3f(pos), new Quaternionf(rot).rotateY((float) Math.toRadians(deg)));
	}

	public Rigid rotZ(float deg) {
		return new Rigid(new Vector3f(pos), new Quaternionf(rot).rotateZ((float) Math.toRadians(deg)));
	}

	public Rigid rotAxis(float deg, Vector3f axis) {
		return new Rigid(new Vector3f(pos), new Quaternionf(rot).rotateAxis((float) Math.toRadians(deg), axis.x, axis.y, axis.z));
	}
}
