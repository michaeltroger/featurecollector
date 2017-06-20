# test scenario:
# FH Hagenberg FH2 main stairs (at library) to 2nd floor
# smartphone in right pocket: display inside, turned around
M =csvread("csv/stairsfhdown2.csv_octave.csv");
time = M(:, 1);


figure (1);
tag = M(:, 2) * 1000;
pressure = M(:, 6);
plot(time,tag,time,pressure)
xlabel('time in s')
legend("tag","pressure")
#xlim([20 33]);
ylim([958.3 959.4]);
print('-dpng', 'octave_print/stairsfhdown2_p.png');

figure (2);
tag = M(:, 2) * 20;
accX = M(:, 7);
accY = M(:, 8);
accZ = M(:, 9);
plot(time,tag,time,accX,time,accY,time,accZ)
xlabel('time in s')
legend("tag","accX","accY","accZ")
#xlim([20 33]);
#ylim([959 959.5]);
print('-dpng', 'octave_print/stairsfhdown2_a.png');

figure (3);
tag = M(:, 2) * 40;
magX = M(:, 3);
magY = M(:, 4);
magZ = M(:, 5);
plot(time,tag,time,magX,time,magY,time,magZ)
xlabel('time in s')
legend("tag","magX","magY","magZ")
#xlim([20 33]);
#ylim([959 959.5]);
print('-dpng', 'octave_print/stairsfhdown2_m.png');

figure (4);
tag = M(:, 2) * 7;
gyrX = M(:, 10);
gyrY = M(:, 11);
gyrZ = M(:, 12);
plot(time,tag,time,gyrX,time,gyrY,time,gyrZ)
xlabel('time in s')
legend("tag","gyrX","gyrY","gyrZ")
#xlim([20 33]);
#ylim([959 959.5]);
print('-dpng', 'octave_print/stairsfhdown2_g.png');


figure (5);
tag = M(:, 2) * 20;
alpha = 0.8;
gravity = zeros(3,1);
linAccX = zeros(length(accX), 1);
linAccY = zeros(length(accY), 1);
linAccZ = zeros(length(accZ), 1);
for i=1:length(linAccX)
  gravity(1) = alpha * gravity(1) + (1 - alpha) * accX(i);
  gravity(2) = alpha * gravity(2) + (1 - alpha) * accY(i);
  gravity(3) = alpha * gravity(3) + (1 - alpha) * accZ(i);
  
  linAccX(i) = accX(i) - gravity(1);
  linAccY(i) = accY(i) - gravity(2);
  linAccZ(i) = accZ(i) - gravity(3);
endfor
plot(time,tag,time,linAccX,time,linAccY,time,linAccZ)
xlabel('time in s')
legend("tag","linAccX","linAccY","linAccZ")
#xlim([20 33]);
#ylim([959 959.5]);
print('-dpng', 'octave_print/stairsfhdown2_la.png');

figure (6);
tag = M(:, 2) * 20;
acc = (accX .^ 2 + accY .^ 2 + accZ .^ 2) .^(1/2);
ewma = zeros(length(acc), 1);
ewma(1) = acc(1);
alpha = 0.001;
for i=2:length(ewma)
  ewma(i) = alpha * acc(i) + (1 - alpha) * ewma(i-1);
endfor
plot(time,tag,time,acc,time,ewma)
xlabel('time in s')
legend("tag","accTotal","ewma")
#xlim([20 33]);
#ylim([959 959.5]);
print('-dpng', 'octave_print/stairsfhdown2_atotal.png');

figure (7);
tag = M(:, 2) * 20;
linAcc = (linAccX .^ 2 + linAccY .^ 2 + linAccZ .^ 2) .^(1/2);
ewma = zeros(length(linAcc), 1);
ewma(1) = linAcc(1);
alpha = 0.001;
for i=2:length(ewma)
  ewma(i) = alpha * linAcc(i) + (1 - alpha) * ewma(i-1);
endfor
plot(time,tag,time,linAcc,time,ewma)
xlabel('time in s')
legend("tag","linAccTotal","ewma")
#xlim([20 33]);
#ylim([959 959.5]);
print('-dpng', 'octave_print/stairsfhdown2_latotal.png');