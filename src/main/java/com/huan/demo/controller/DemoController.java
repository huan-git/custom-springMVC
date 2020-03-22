package com.huan.demo.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.huan.demo.service.DemoService;
import com.huan.mcvframework.HAutowired;
import com.huan.mcvframework.HController;
import com.huan.mcvframework.HRequestMapping;

@HController
@HRequestMapping("/demo")
public class DemoController {
	
	@HAutowired
	DemoService demoService;
	
	@HRequestMapping("/demo")
	public void doman(HttpServletRequest req, HttpServletResponse resp) {
		try {
			resp.getWriter().println(demoService.say());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
