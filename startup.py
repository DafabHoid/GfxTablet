#!/usr/bin/env python
from configparser import ConfigParser
import sys, os
import subprocess, shlex
import atexit

config = ConfigParser()

configfile = "config.ini"
if len(sys.argv) > 1:
	configfile = sys.argv[1]
config.read(configfile)

main_cfg = config["main"]
screencapture_cfg = config["ScreenCapture"]
output_tmp_dir = "/dev/shm/screenshare/"

def terminateChildProcesses():
	try:
		os.rmdir("/dev/shm/screenshare/")
	except:
		pass
	if networktablet:
		networktablet.terminate()
	if http_server:
		http_server.terminate()

http_server = None
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
	try:
		os.mkdir(output_tmp_dir)
	except:
		print("Creating output directory {} failed!".format(output_tmp_dir))
		sys.exit(1)

	command = "python -m http.server 8080 --directory " + output_tmp_dir
	cmdline = shlex.split(command)
	try:
		http_server = subprocess.Popen(cmdline, stdout=sys.stdout, stderr=sys.stderr)
	except OSError as e:
		print("Starting the RTSP server failed:")
		print(e)
		sys.exit(1)
	
	command = "ffmpeg -f x11grab -framerate {} -i :0.0 -s {} -pix_fmt yuv420p -an -c:v {} {} -tune:v zerolatency -preset veryfast -g 30 -f dash -window_size 10 -remove_at_exit 1 -use_timeline 1 -utc_timing_url https://time.akamai.com/?iso -seg_duration 1 -streaming 1 -index_correction 1 -target_latency 0.5 -ldash 1 /dev/shm/screenshare/screen.mpd"
	command = command.format(screencapture_cfg["Framerate"], screencapture_cfg["Resolution"], screencapture_cfg["VideoCodec"], screencapture_cfg["CodecOptions"])
	print("Using ffmpeg command: {}".format(command))
	try:
		cmdline = shlex.split(command)
		subprocess.run(cmdline, stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr)
	except OSError as e:
		print("Starting ffmpeg for screen recording failed:")
		print(e)
		sys.exit(1)