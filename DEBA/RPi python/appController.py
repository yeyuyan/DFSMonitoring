import bluetoothController
import subprocess
from bluetooth import *

sub = subprocess.Popen("sudo hciconfig hci0 up",shell = True)
sub = subprocess.Popen("sudo hciconfig hci0 piscan",shell = True)

server_sock = BluetoothSocket(RFCOMM)
server_sock.bind(("",PORT_ANY))
server_sock.listen(1)

server_thread = bluetoothController.ServerThread(server_sock)
server_thread.start()


