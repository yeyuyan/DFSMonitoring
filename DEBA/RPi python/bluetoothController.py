from bluetooth import *
import threading
import subprocess

sub = subprocess.Popen("sudo hciconfig hci0 up",shell = True)
sub = subprocess.Popen("sudo hciconfig hci0 piscan",shell = True)

server_sock = BluetoothSocket(RFCOMM)
server_sock.bind(("",PORT_ANY))
server_sock.listen(1)

def serverThread():
    while True:
        try:
            client_sock,(client_addr,client_ch) = server_sock.accept()
            send(client_sock,"Acceped connection.")
            print ("Accepted connection from", client_addr)
            commu_thread  = threading.Thread(target = commuThread, args = (client_sock,client_addr))
            commu_thread.setDaemon(True)
            commu_thread.start()
        except:
            print("accept() method failed")
            break

def commuThread(sock,addr):
    while True:
        data_recv = sock.recv(1024)
        if len(data_recv)!= 0:
            print("received [%s]" %data_recv)
            send(sock,"data received")

def send(sock,data):
        #Method to send data to DMA(will be called when a message needs to be sent)
        #data:string
        try:
            sock.send(data)
            print("Socket has sucessfully sent message.")
        except:
            print("socket cannot send data successfully.")

t = threading.Thread(target = serverThread, args =())
t.start()




"""
class serverThread(threading.Thread):
    #Server thread to wait for the connection of mobile
    def __init__(self,server_sock):
        threading.Thread.__init__(self)
        self.server_sock = server_sock

    def run(self):
        while True:
            try:
                client_sock,(client_addr,client_ch) = (self.server_sock).accept()
                send(client_sock,"Acceped connection.")
                print ("Accepted connection from", client_addr)
                commu_thread  = commuThread(client_sock,client_addr)
                commu_thread.setDaemon(True)
                commu_thread.start()
            except:
                print("accept() method failed")
                break   
        



class commuThread(threading.Thread):
     # Thread to receive data from DMdA
    def __init__(self,sock,addr):
        threading.Thread.__init__(self)
        self.sock = sock
        self.addr = addr

    def run(self):
         while True:
            data_recv = sock.recv(1024)
            if len(data_recv)!= 0:
                print("received [%s]" %data_recv)
                send(sock,"data received")


    
t = serverThread(server_sock)
time.sleep(2)
t.start()"""
