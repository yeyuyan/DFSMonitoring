from bluetooth import *
import threading
import subprocess

class serverThread(threading.Thread):
    #Server thread to wait for the connection of mobile
    def __init__(self,server_sock):
        threading.Thread.__init__(self)
        self.server_sock = server_sock

    def run(self):

        while True:
            try:
                client_sock,(client_addr,client_ch) = self.server_sock.accept()
                print ("Accepted connection from", client_addr)
                   
            except:
                print("accept() method failed")
                close_bt()
                break

            try:
                client_sock.send("Raspberry pi accepts connection")
            except:
                print("send failed")

            commu_thread  = commuThread(client_sock,client_addr)
            commu_thread.setDaemon(True)
            commu_thread.start()



class commuThread(threading.Thread):
     # Thread to receive data from DMdA
    def __init__(self,sock,addr):
        threading.Thread.__init__(self)
        self.sock = sock
        self.addr = addr

    def run(self):
         while True:
            data_recv = self.sock.recv(1024)
            if len(data_recv)!= 0:
                print("received [%s]" %data_recv)
                self.sock.send("data received")



def open_bt():
    subprocess.Popen("sudo hciconfig hci0 up",shell = True)
    subprocess.Popen("sudo hciconfig hci0 piscan",shell = True)

def close_bt():
    subprocess.Popen("sudo hciconfig hci0 down",shell = True)
    subprocess.Popen("sudo hciconfig hci0 noscan",shell = True)

def build_server_socket():
    server_sock = BluetoothSocket(RFCOMM)
    server_sock.bind(("",PORT_ANY))
    server_sock.listen(1)
    return server_sock



open_bt()
server_sock = build_server_socket()

t = serverThread(server_sock)
t.start()
while(True):
    if t.isAlive() is False:
        close_bt()
        break



