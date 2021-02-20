#!/usr/bin/env python
from configparser import ConfigParser
import sys
import subprocess, shlex
import atexit

config = ConfigParser()

configfile = "config.ini"
if len(sys.argv) > 1:
	configfile = sys.argv[1]
config.read(configfile)

main_cfg = config["main"]
screencapture_cfg = config["ScreenCapture"]

def terminateChildProcesses():
	if networktablet:
		networktablet.terminate()
	if rtsp_server:
		rtsp_server.terminate()

rtsp_server = None
networktablet = None
atexit.register(terminateChildProcesses)

try:
	command = "driver-uinput/networktablet"
	networktablet = subprocess.Popen(command, stderr=sys.stderr)
except OSError as e:
	print("Starting networktablet failed:")
	print(e)
	sys.exit(1)

if main_cfg.getboolean("ScreenShare"):
	command = "videoserver/rtsp-simple-server"
	try:
		rtsp_server = subprocess.Popen(command, stdout=sys.stdout, stderr=sys.stderr)
	except OSError as e:
		print("Starting the RTSP server failed:")
		print(e)
		sys.exit(1)
	
	command = "ffmpeg -f x11grab -framerate {} -i :0.0 -s {} -pix_fmt yuv420p -an -c:v {} {} -tune:v zerolatency -preset veryfast -f rtsp rtsp://[::1]:8554/screen"
	command = command.format(screencapture_cfg["Framerate"], screencapture_cfg["Resolution"], screencapture_cfg["VideoCodec"], screencapture_cfg["CodecOptions"])
	print("Using ffmpeg command: {}".format(command))
	try:
		cmdline = shlex.split(command)
		subprocess.run(cmdline, stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr)
	except OSError as e:
		print("Starting ffmpeg for screen recording failed:")
		print(e)
		sys.exit(1)