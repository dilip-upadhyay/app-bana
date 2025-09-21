import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatRadioModule } from '@angular/material/radio';

@Component({
  selector: 'ab-radio',
  standalone: true,
  imports: [CommonModule, MatRadioModule],
  templateUrl: './radio.html',
  styleUrl: './radio.css'
})
export class Radio {
  @Input() options: { value: any; label: string }[] = [];
  @Input() value: any;
  @Input() disabled: boolean = false;
  @Output() valueChange = new EventEmitter<any>();
}
