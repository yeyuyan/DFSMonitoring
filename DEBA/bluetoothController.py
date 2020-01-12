from bluetooth import *
import threading
import subprocess

import logging
logging.basicConfig(level = logging.DEBUG,
        format = '%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s) %(threadName)s: %(message)s',
        datefmt = '%d %b %Y %H:%M:%S',
        filename = 'log_rpi.log',filemode = 'w')



class serverThread(threading.Thread):
    #Server thread to wait for the connection of mobile
    def __init__(self,name,server_sock):
        threading.Thread.__init__(self)
        self.name = name
        self.server_sock = server_sock
        self.commu_thread = None
        self.f_is_flying = False# a var to store the status of the plane
        self.__running = threading.Event()
        self.__running.set()

    def run(self):
        logging.debug('started.')
        while self.__running.isSet():
            try:
                client_sock,(client_addr,client_ch) = self.server_sock.accept()
                client_sock.send("Raspberry pi accepts connection")
                logging.info('Accepted connection from' + client_addr)
            except:
                logging.error("accept() method failed")
                close_bt()
                break
            if self.f_is_flying:
                client_sock.send("is_flying")
            self.commu_thread = commuThread('commu_thread',client_sock,client_addr)
            self.commu_thread.setDaemon(True)
            self.commu_thread.start()

    def stop(self):
        self.__running.clear()
        server_sock.close()
        logging.debug('stoped.')


class commuThread(threading.Thread):
     # Thread to receive data from DMA
    
    def __init__(self,name,sock,addr):
        threading.Thread.__init__(self)
        self.name = name
        self.sock = sock
        self.addr = addr
        self.data_recv =[]
        self.__running = threading.Event()
        self.__running.set()
    
    def run(self):
        logging.debug('started.')
        while self.__running.isSet():
            try:
                data_recv = self.sock.recv(1024)
                if len(data_recv)!= 0:
                    self.data_recv.append(data_recv)
                    logging.info("received [%s]" %data_recv)
                    self.send_msg("data received")
            except:
                logging.error("Client socket closed.")

    def send_file(self,fileName):
        self.sock.send("file:"+fileName)
        with open(fileName,'r') as f:
            content = f.read()
            self.sock.send(content)
        f.close()
        logging.info('log file was sent successfully.')
    
    def send_msg(self,string):
        self.sock.send(string)
        logging.debug('sent message to client: ' + string)

    def stop(self):
        self.__running.clear()
        self.sock.close()
        self.sock = None
        logging.debug('stoped.')
    
def open_bt():
    subprocess.Popen("sudo hciconfig hci0 up",shell = True)  
    logging.debug('bluetooth opened')
    subprocess.Popen("sudo hciconfig hci0 piscan",shell = True)
    logging.debug('enabled discovery')

def close_bt():
    subprocess.Popen("sudo hciconfig hci0 noscan",shell = True)
    logging.debug('disabed discovery')
    subprocess.Popen("sudo hciconfig hci0 down",shell = True)
    logging.debug('bluetooth closed')
    
def build_server_socket():
    server_sock = BluetoothSocket(RFCOMM)
    logging.debug('socket builed.')
    server_sock.bind(("",PORT_ANY))
    server_sock.listen(1)
    logging.debug('socket set to listen.')
    return server_sock


