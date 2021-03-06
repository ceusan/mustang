package com.dimogo.open.myjobs.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.gs.collections.api.list.ImmutableList;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

import java.util.*;

/**
 * Created by Ethan Xiao on 2017/4/7.
 */
public class JobUtils {

	public static JobParameters mergeConfJobParas(String job, JobParameters jobParameters) {
		String paras = loadConfJobParas(job);
		if (StringUtils.isBlank(paras)) {
			return jobParameters;
		}
		Map<String, JobParameter> userParas = jobParameters.getParameters();
		JSONObject confParas = JSON.parseObject(paras);
		for (String para : confParas.keySet()) {
			if (userParas.containsKey(para)) {
				continue;
			}
			userParas.put(para, new JobParameter(confParas.getString(para)));
		}
		return new JobParameters(userParas);
	}

	public static String loadConfJobParas(String job) {
		ZkClient zkClient = ZKUtils.newClient();
		try {
			String data = zkClient.readData(ZKUtils.buildJobParasPath(job), true);
			return data;
		} finally {
			zkClient.close();
		}
	}

	public static void setConfJobParas(String job, String paras) {
		ZkClient zkClient = ZKUtils.newClient();
		try {
			String path = ZKUtils.buildJobParasPath(job);
			if (zkClient.exists(path)) {
				zkClient.writeData(path, paras);
				return;
			}
			zkClient.create(path, paras, CreateMode.PERSISTENT);
		} finally {
			zkClient.close();
		}
	}

	public static void setJobCronExpression(String job, String cronExpression) {
		ZkClient zkClient = ZKUtils.newClient();
		try {
			String path = ZKUtils.buildJobCronPath(job);
			if (zkClient.exists(path)) {
				zkClient.writeData(path, cronExpression);
				return;
			}
			zkClient.create(path, cronExpression, CreateMode.PERSISTENT);
		} finally {
			zkClient.close();
		}
	}

	public static void cleanJobCronExpression(String job) {
		ZkClient zkClient = ZKUtils.newClient();
		try {
			zkClient.delete(ZKUtils.buildJobCronPath(job));
		} finally {
			zkClient.close();
		}
	}

	public static void sendJobNotification(String job, String notification, Object data, Collection<String> slaves) throws Exception {
		ZkClient zkClient = ZKUtils.newClient();
		try {
			if (CollectionUtils.isEmpty(slaves)) {
				zkClient.create(ZKUtils.buildNotificationPath(notification), data, CreateMode.PERSISTENT_SEQUENTIAL);
				return;
			}
			Map.Entry<List<ACL>, Stat> entry = zkClient.getAcl(ZKUtils.Path.Notifications.build());
			List<ACL> acl = entry.getKey();
			List<Op> ops = new ArrayList<Op>(slaves.size() + 2);
			byte[] notificationData = data == null ? null : ZKUtils.getSerializableSerializer().serialize(data);
			notification = notification + "_" + job + "_" + ((int)(Math.random() * 100000));
			ops.add(Op.create(ZKUtils.buildNotificationPath(notification), notificationData, acl, CreateMode.PERSISTENT));
			ops.add(Op.create(ZKUtils.buildNotificationSlavesPath(notification), null, acl, CreateMode.PERSISTENT));
			for (String slave : slaves) {
				ops.add(Op.create(ZKUtils.buildNotificationSlavePath(notification, slave), null, acl, CreateMode.PERSISTENT));
			}
			zkClient.multi(ops);
		} finally {
			zkClient.close();
		}
	}

	public static void deleteJobNotification(String notification) {
		ZkClient zkClient = ZKUtils.newClient();
		try {
			zkClient.delete(ZKUtils.buildNotificationPath(notification));
		} finally {
			zkClient.close();
		}
	}

	public static String jsonToParameterList(JSONObject jsonPara) {
		if (jsonPara == null) {
			return StringUtils.EMPTY;
		}
		StringBuilder paraList = new StringBuilder();
		for (String key : jsonPara.keySet()) {
			if (paraList.length() > 0) {
				paraList.append("\r\n");
			}
			paraList.append(key).append("=").append(jsonPara.getString(key));
		}
		return paraList.toString();
	}

	public static JSONObject parameterListToJson(String parameterList) {
		JSONObject paras = new JSONObject();
		if (StringUtils.isBlank(parameterList)) {
			return paras;
		}
		parameterList = parameterList.replaceAll("\r", "");
		for (String pair : parameterList.split("\n")) {
			String[] kv = pair.split("=");
			paras.put(StringUtils.trim(kv[0]), StringUtils.trim(kv[1]));
		}
		return paras;
	}
}
